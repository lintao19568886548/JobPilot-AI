import hashlib
import math
import threading
import time
from dataclasses import dataclass
from functools import lru_cache

from pymilvus import DataType, MilvusClient
from sentence_transformers import SentenceTransformer

from .config import Settings
from .schemas import EmbeddingDocument, EmbeddingRecord, EmbeddingRequest, EmbeddingResponse


@dataclass(frozen=True)
class StoredEmbedding:
    record: EmbeddingRecord
    vector: list[float]


class BgeM3EmbeddingProvider:
    def __init__(self, settings: Settings):
        if settings.embedding_model != "BAAI/bge-m3":
            raise ValueError("Phase 3 production embedding model must be BAAI/bge-m3")
        self.settings = settings
        self._model: SentenceTransformer | None = None
        self._lock = threading.Lock()

    def _load(self) -> SentenceTransformer:
        if self._model is None:
            with self._lock:
                if self._model is None:
                    self._model = SentenceTransformer(self.settings.embedding_model, device=self.settings.embedding_device)
        return self._model

    def encode(self, texts: list[str]) -> list[list[float]]:
        cleaned = [text.strip()[: self.settings.embedding_max_chars] for text in texts]
        vectors = self._load().encode(cleaned, normalize_embeddings=True, convert_to_numpy=True, show_progress_bar=False)
        result = [[float(value) for value in row] for row in vectors]
        if any(len(row) != self.settings.embedding_dimension for row in result):
            raise ValueError("Embedding dimension does not match EMBEDDING_DIMENSION")
        return result


class MilvusVectorStore:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.client = MilvusClient(uri=settings.milvus_uri)
        self._lock = threading.Lock()
        self.ensure_collection()

    def ensure_collection(self) -> None:
        name = self.settings.milvus_collection
        with self._lock:
            if self.client.has_collection(name):
                description = self.client.describe_collection(name)
                vector_field = next(field for field in description["fields"] if field["name"] == "vector")
                if int(vector_field["params"]["dim"]) != self.settings.embedding_dimension:
                    raise ValueError("Existing Milvus collection dimension is incompatible")
                self.client.load_collection(name)
                return
            schema = self.client.create_schema(auto_id=False, enable_dynamic_field=False)
            schema.add_field("vector_id", DataType.VARCHAR, is_primary=True, max_length=100)
            schema.add_field("user_id", DataType.INT64)
            schema.add_field("entity_type", DataType.VARCHAR, max_length=32)
            schema.add_field("entity_public_id", DataType.VARCHAR, max_length=26)
            schema.add_field("content_hash", DataType.VARCHAR, max_length=64)
            schema.add_field("model", DataType.VARCHAR, max_length=160)
            schema.add_field("created_at_ms", DataType.INT64)
            schema.add_field("vector", DataType.FLOAT_VECTOR, dim=self.settings.embedding_dimension)
            indexes = self.client.prepare_index_params()
            indexes.add_index(field_name="vector", index_type="AUTOINDEX", metric_type="COSINE")
            self.client.create_collection(collection_name=name, schema=schema, index_params=indexes, consistency_level="Strong")

    def get(self, vector_id: str) -> list[float] | None:
        rows = self.client.get(collection_name=self.settings.milvus_collection, ids=[vector_id], output_fields=["vector"])
        if not rows:
            return None
        return [float(value) for value in rows[0]["vector"]]

    def upsert(self, user_id: int, document: EmbeddingDocument, vector_id: str, vector: list[float]) -> None:
        self.client.upsert(
            collection_name=self.settings.milvus_collection,
            data=[
                {
                    "vector_id": vector_id,
                    "user_id": user_id,
                    "entity_type": document.entityType,
                    "entity_public_id": document.entityPublicId,
                    "content_hash": document.contentHash,
                    "model": self.settings.embedding_model,
                    "created_at_ms": int(time.time() * 1000),
                    "vector": vector,
                }
            ],
        )

    def count(self) -> int:
        stats = self.client.get_collection_stats(self.settings.milvus_collection)
        return int(stats.get("row_count", 0))


class EmbeddingService:
    def __init__(self, settings: Settings, provider: BgeM3EmbeddingProvider | None = None, store: MilvusVectorStore | None = None):
        self.settings = settings
        self.provider = provider or BgeM3EmbeddingProvider(settings)
        self.store = store or MilvusVectorStore(settings)

    def embed(self, request: EmbeddingRequest) -> tuple[EmbeddingResponse, list[StoredEmbedding]]:
        started = time.perf_counter()
        stored: list[StoredEmbedding | None] = [None] * len(request.documents)
        misses: list[tuple[int, EmbeddingDocument, str]] = []
        for index, document in enumerate(request.documents):
            expected_hash = hashlib.sha256(document.text.encode("utf-8")).hexdigest()
            if not document.contentHash == expected_hash:
                raise ValueError("Embedding document contentHash mismatch")
            vector_id = hashlib.sha256(
                f"{request.userId}:{document.entityType}:{document.entityId}:{self.settings.embedding_model}:{document.contentHash}".encode()
            ).hexdigest()
            cached = self.store.get(vector_id)
            if cached is None:
                misses.append((index, document, vector_id))
            else:
                stored[index] = StoredEmbedding(self._record(document, vector_id, True), cached)
        if misses:
            vectors = self.provider.encode([document.text for _, document, _ in misses])
            for (index, document, vector_id), vector in zip(misses, vectors, strict=True):
                self.store.upsert(request.userId, document, vector_id, vector)
                stored[index] = StoredEmbedding(self._record(document, vector_id, False), vector)
        complete = [item for item in stored if item is not None]
        response = EmbeddingResponse(
            records=[item.record for item in complete],
            provider=self.settings.embedding_provider,
            model=self.settings.embedding_model,
            dimension=self.settings.embedding_dimension,
            embeddingVersion=self.settings.embedding_version,
            elapsedMs=max(0, int((time.perf_counter() - started) * 1000)),
        )
        return response, complete

    def cosine_score(self, left: list[float], right: list[float]) -> float:
        dot = sum(a * b for a, b in zip(left, right, strict=True))
        left_norm = math.sqrt(sum(value * value for value in left))
        right_norm = math.sqrt(sum(value * value for value in right))
        cosine = 0.0 if left_norm == 0 or right_norm == 0 else dot / (left_norm * right_norm)
        return round(max(0.0, min(100.0, (cosine + 1.0) * 50.0)), 2)

    def _record(self, document: EmbeddingDocument, vector_id: str, cache_hit: bool) -> EmbeddingRecord:
        return EmbeddingRecord(
            entityType=document.entityType,
            entityId=document.entityId,
            entityPublicId=document.entityPublicId,
            vectorId=vector_id,
            contentHash=document.contentHash,
            collectionName=self.settings.milvus_collection,
            provider=self.settings.embedding_provider,
            model=self.settings.embedding_model,
            dimension=self.settings.embedding_dimension,
            embeddingVersion=self.settings.embedding_version,
            cacheHit=cache_hit,
        )


@lru_cache
def get_embedding_service(
    settings_key: tuple[str, str, int, str, str, str, str, int]
) -> EmbeddingService:
    model, provider, dimension, version, device, uri, collection, max_chars = settings_key
    settings = Settings(
        EMBEDDING_MODEL=model,
        EMBEDDING_PROVIDER=provider,
        EMBEDDING_DIMENSION=dimension,
        EMBEDDING_VERSION=version,
        EMBEDDING_DEVICE=device,
        MILVUS_URI=uri,
        MILVUS_COLLECTION=collection,
        EMBEDDING_MAX_CHARS=max_chars,
    )
    return EmbeddingService(settings)


def service_for(settings: Settings) -> EmbeddingService:
    return get_embedding_service(
        (
            settings.embedding_model,
            settings.embedding_provider,
            settings.embedding_dimension,
            settings.embedding_version,
            settings.embedding_device,
            settings.milvus_uri,
            settings.milvus_collection,
            settings.embedding_max_chars,
        )
    )
