FROM python:3.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    HF_HOME=/models/huggingface

RUN groupadd --system --gid 10002 jobpilot \
    && useradd --system --uid 10002 --gid 10002 --home-dir /app --shell /usr/sbin/nologin jobpilot \
    && mkdir -p /app /models/huggingface \
    && chown -R 10002:10002 /app /models

WORKDIR /app
COPY ai-service/requirements.txt ./requirements.txt
ARG TORCH_WHEEL_SHA256=6746dbcbeb526eb61330b76b41ff1b4eb848951103a892eeb080dfa2b264667b
COPY ai-service/vendor/torch-2.13.0+cpu-cp311-cp311-manylinux_2_28_x86_64.whl /tmp/torch-2.13.0+cpu-cp311-cp311-manylinux_2_28_x86_64.whl
# This service performs local CPU inference. Resolve Torch's ordinary Python
# dependencies from PyPI and install a pre-fetched official CPU wheel only after
# checking the digest published by the Torch index. The wheel is local-only and
# gitignored so transient network failures can resume without weakening checks.
RUN --mount=type=cache,target=/root/.cache/pip \
    python -m pip install --timeout 120 --retries 20 \
        filelock \
        "typing-extensions>=4.10.0" \
        "sympy>=1.13.3" \
        "networkx>=2.5.1" \
        jinja2 \
        "fsspec>=0.8.5" \
    && echo "${TORCH_WHEEL_SHA256}  /tmp/torch-2.13.0+cpu-cp311-cp311-manylinux_2_28_x86_64.whl" | sha256sum --check --strict \
    && python -m pip install --timeout 120 --retries 20 --no-deps \
        /tmp/torch-2.13.0+cpu-cp311-cp311-manylinux_2_28_x86_64.whl \
    && python -m pip install --timeout 120 --retries 20 --requirement requirements.txt
COPY --chown=10002:10002 ai-service/app ./app

USER 10002:10002
EXPOSE 8010
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8010", "--no-access-log"]
