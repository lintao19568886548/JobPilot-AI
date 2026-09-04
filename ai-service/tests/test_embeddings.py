import hashlib

import pytest

from app.config import Settings
from app.embeddings import EmbeddingService
from app.schemas import EmbeddingDocument, EmbeddingRequest


class FakeProvider:
    def __init__(self):
        self.calls = 0

    def encode(self, texts):
        self.calls += 1
        return [[1.0, 0.0, 0.0] if "Java" in text else [0.0, 1.0, 0.0] for text in texts]


class FakeStore:
    def __init__(self):
        self.rows = {}

    def get(self, vector_id):
        return self.rows.get(vector_id)

    def upsert(self, user_id, document, vector_id, vector):
        self.rows[vector_id] = vector


def document(text="Java Spring Boot"):
    return EmbeddingDocument(
        entityType="JOB",
        entityId=1,
        entityPublicId="01TESTDOCUMENT000000000001",
        text=text,
        contentHash=hashlib.sha256(text.encode()).hexdigest(),
    )


def test_embedding_cache_reuses_vector_by_content_hash():
    provider, store = FakeProvider(), FakeStore()
    service = EmbeddingService(Settings(embedding_dimension=3), provider, store)
    request = EmbeddingRequest(taskId="task", userId=1, documents=[document()])

    first, _ = service.embed(request)
    second, _ = service.embed(request)

    assert first.records[0].cacheHit is False
    assert second.records[0].cacheHit is True
    assert provider.calls == 1
    assert len(store.rows) == 1


def test_embedding_rejects_forged_content_hash():
    service = EmbeddingService(Settings(embedding_dimension=3), FakeProvider(), FakeStore())
    bad = document().model_copy(update={"contentHash": "0" * 64})
    with pytest.raises(ValueError, match="contentHash mismatch"):
        service.embed(EmbeddingRequest(taskId="task", userId=1, documents=[bad]))


@pytest.mark.parametrize(
    ("left", "right", "expected"),
    [([1.0, 0.0], [1.0, 0.0], 100.0), ([1.0, 0.0], [0.0, 1.0], 50.0), ([1.0, 0.0], [-1.0, 0.0], 0.0)],
)
def test_cosine_score_is_bounded(left, right, expected):
    service = EmbeddingService(Settings(embedding_dimension=3), FakeProvider(), FakeStore())
    assert service.cosine_score(left, right) == expected
