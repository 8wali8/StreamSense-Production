FROM python:3.11.16-slim@sha256:9534e5a8e315485d4061ed659af0fd78a284c015f9b73661b41d6bab25604534

# uv comes from its release image so the build never invokes pip.
COPY --from=ghcr.io/astral-sh/uv:0.12.9@sha256:8b940d3a9d65bed080436972241af2e21c84b5e8c9193f7014ed71479ee795ff /uv /uvx /bin/

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never

# Install the locked runtime dependencies before copying source so the layer is cached
# across code changes. The dev group (pytest, ruff) is never installed here.
COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --locked --no-dev

COPY src/main/python ./src/main/python

ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONPATH=/app/src/main/python

# Run as a dedicated non-root user; ffmpeg and streamlink scratch files go under HOME.
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid 10001 --no-create-home app \
    && mkdir -p /tmp/app && chown -R 10001:10001 /app /tmp/app
ENV HOME=/tmp/app
USER 10001

EXPOSE 8090

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090"]
