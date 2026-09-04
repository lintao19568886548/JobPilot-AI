import argparse
import json
from pathlib import Path

from pymilvus import DataType, MilvusClient


FIELDS = ["vector_id", "user_id", "entity_type", "entity_public_id", "content_hash", "model", "created_at_ms", "vector"]


def export(uri: str, output: Path) -> dict:
    client = MilvusClient(uri=uri)
    collections = []
    for name in client.list_collections():
        description = client.describe_collection(name)
        stats = client.get_collection_stats(name)
        row_count = int(stats.get("row_count", 0))
        vector = next((field for field in description.get("fields", []) if field.get("name") == "vector"), None)
        dimension = int((vector or {}).get("params", {}).get("dim", 0))
        records = []
        if row_count:
            client.load_collection(name)
            records = client.query(name, filter="created_at_ms >= 0", output_fields=FIELDS, limit=min(row_count, 16384))
        collections.append({"name": name, "dimension": dimension, "rowCount": row_count, "records": records})
    result = {"schemaVersion": "phase11-milvus-export-v1", "collections": collections}
    output.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return {"collections": len(collections), "rows": sum(item["rowCount"] for item in collections)}


def restore_drill(uri: str, source: Path, prefix: str) -> dict:
    data = json.loads(source.read_text(encoding="utf-8"))
    if data.get("schemaVersion") != "phase11-milvus-export-v1":
        raise ValueError("Unsupported Milvus backup schema")
    client = MilvusClient(uri=uri)
    restored = 0
    temporary = []
    try:
        for index, item in enumerate(data["collections"]):
            name = f"{prefix}_{index}"
            if not name.startswith("jobpilot_restore_drill_"):
                raise ValueError("Unsafe temporary collection prefix")
            dimension = int(item["dimension"])
            if dimension <= 0:
                continue
            schema = client.create_schema(auto_id=False, enable_dynamic_field=False)
            schema.add_field("vector_id", DataType.VARCHAR, is_primary=True, max_length=100)
            schema.add_field("user_id", DataType.INT64)
            schema.add_field("entity_type", DataType.VARCHAR, max_length=32)
            schema.add_field("entity_public_id", DataType.VARCHAR, max_length=26)
            schema.add_field("content_hash", DataType.VARCHAR, max_length=64)
            schema.add_field("model", DataType.VARCHAR, max_length=160)
            schema.add_field("created_at_ms", DataType.INT64)
            schema.add_field("vector", DataType.FLOAT_VECTOR, dim=dimension)
            indexes = client.prepare_index_params()
            indexes.add_index(field_name="vector", index_type="AUTOINDEX", metric_type="COSINE")
            client.create_collection(name, schema=schema, index_params=indexes, consistency_level="Strong")
            temporary.append(name)
            if item["records"]:
                client.insert(name, item["records"])
                client.flush(name)
            actual = int(client.get_collection_stats(name).get("row_count", 0))
            if actual != len(item["records"]):
                raise RuntimeError(f"Milvus restore count mismatch for {name}")
            restored += actual
        return {"collections": len(temporary), "rows": restored}
    finally:
        for name in temporary:
            if client.has_collection(name):
                client.drop_collection(name)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["export", "restore-drill"])
    parser.add_argument("--uri", required=True)
    parser.add_argument("--file", required=True, type=Path)
    parser.add_argument("--prefix", default="jobpilot_restore_drill_manual")
    args = parser.parse_args()
    result = export(args.uri, args.file) if args.command == "export" else restore_drill(args.uri, args.file, args.prefix)
    print(json.dumps(result, separators=(",", ":")))


if __name__ == "__main__":
    main()
