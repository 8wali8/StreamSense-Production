FROM python:3.11.16-slim@sha256:9534e5a8e315485d4061ed659af0fd78a284c015f9b73661b41d6bab25604534

# uv comes from its release image so the build never invokes pip.
COPY --from=ghcr.io/astral-sh/uv:0.12.9@sha256:8b940d3a9d65bed080436972241af2e21c84b5e8c9193f7014ed71479ee795ff /uv /uvx /bin/

WORKDIR /app

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never

# Install the locked runtime dependencies before copying source so the layer is cached across
# code changes; the dev group is never installed here. The second sync installs the service itself
# into the venv as a regular (non-editable) package, so nothing depends on PYTHONPATH or the cwd.
COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --locked --no-dev --no-install-project

COPY src ./src
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --locked --no-dev --no-editable

ENV PATH="/app/.venv/bin:$PATH"

# Run as a dedicated non-root user. The model cache directories are created and owned here so a
# fresh Compose volume inherits that ownership; Kubernetes sets fsGroup 10001 on the PVCs instead.
# /app stays root-owned and read-only to the service (it only imports from the venv); a chown -R of
# the venv would copy the whole dependency layer into this one and double the image.
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid 10001 --no-create-home app \
    && mkdir -p /models /tmp/app && chown -R 10001:10001 /models /tmp/app
ENV HOME=/tmp/app
USER 10001

CMD ["uvicorn", "ml_engine.main:app", "--host", "0.0.0.0", "--port", "8000"]
