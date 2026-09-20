import subprocess
import sys
import time

import pytest

from video_capture_service.process import run_bounded


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


def test_run_bounded_kills_the_process_when_cancelled():
    import threading

    from video_capture_service.process import ProcessCancelledError

    cancel = threading.Event()
    threading.Timer(0.3, cancel.set).start()
    started = time.monotonic()

    with pytest.raises(ProcessCancelledError):
        run_bounded([sys.executable, "-c", "import time; time.sleep(30)"], 20, cancel)

    # Ended on the cancel, not on the 20 s timeout and nowhere near the child's 30 s sleep.
    assert time.monotonic() - started < 5


def test_run_bounded_ignores_a_cancel_that_never_comes():
    import threading

    result = run_bounded([sys.executable, "-c", "print('done')"], 10, threading.Event())

    assert result.returncode == 0
    assert result.stdout.strip() == "done"
