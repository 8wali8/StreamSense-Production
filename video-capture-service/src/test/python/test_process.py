import subprocess
import sys
import time

import pytest

from app.process import run_bounded


def test_run_bounded_returns_completed_process():
    result = run_bounded(
        [sys.executable, "-c", "import sys; print('out'); print('err', file=sys.stderr); sys.exit(3)"], 10
    )

    assert isinstance(result, subprocess.CompletedProcess)
    assert result.returncode == 3
    assert result.stdout.strip() == "out"
    assert result.stderr.strip() == "err"


def test_run_bounded_kills_the_process_on_timeout():
    started = time.monotonic()

    with pytest.raises(subprocess.TimeoutExpired):
        run_bounded([sys.executable, "-c", "import time; time.sleep(30)"], 0.5)

    # The child is gone and we did not wait anywhere near its 30 s sleep.
    assert time.monotonic() - started < 10
