import pytest
from fastapi.testclient import TestClient

from app.engine import Engine, make_windows, train, FEATURES


@pytest.fixture(scope="module")
def engine(tmp_path_factory):
    d = tmp_path_factory.mktemp("models")
    metrics = train(str(d))
    assert metrics["sms_accuracy"] > 0.95
    assert metrics["attack_detection_rate"] > 0.9
    assert metrics["normal_false_alarm_rate"] < 0.05
    return Engine(str(d))


def w(**kw):
    base = dict(msgs_per_min=0.3, otp_ratio=0.1, distinct_senders=1, calls_per_hour=1,
                repeat_number_ratio=0.05, unrequested_otp_ratio=0.05)
    base.update(kw)
    return base


def test_spam_vs_legit_otp(engine):
    assert engine.spam_prob("URGENT! You won a free prize, click http://bit.ly/abc to claim") > 0.8
    assert engine.spam_prob("Your OTP is 482913. Do not share it with anyone.") < 0.3


def test_normal_window_is_normal(engine):
    assert engine.risk(w())["label"] == "normal"


def test_attack_window_is_suspicious(engine):
    r = engine.risk(w(msgs_per_min=15, otp_ratio=0.9, distinct_senders=12, calls_per_hour=14,
                      repeat_number_ratio=0.7, unrequested_otp_ratio=0.9))
    assert r["label"] == "suspicious", r


def test_frequency_alone_is_never_suspicious(engine):
    r = engine.risk(w(msgs_per_min=40, calls_per_hour=30))
    assert r["label"] != "suspicious", r
    assert r["risk"] <= 0.49


def test_legit_otp_retry_is_not_suspicious(engine):
    r = engine.risk(w(msgs_per_min=2.5, otp_ratio=0.95, distinct_senders=1, unrequested_otp_ratio=0.02))
    assert r["label"] != "suspicious", r


def test_api(engine, monkeypatch, tmp_path):
    monkeypatch.setenv("MODEL_DIR", str(tmp_path))
    import importlib, app.main as m
    importlib.reload(m)
    c = TestClient(m.app)
    assert c.get("/health").json() == {"status": "ok"}
    r = c.post("/score/sms", json={"text": "Lunch at 1?"})
    assert r.status_code == 200 and r.json()["spam_probability"] < 0.5
    r = c.post("/score/window", json=w(sample_texts=["Your OTP is 123456"]))
    assert r.status_code == 200 and r.json()["label"] == "normal"
    assert c.post("/score/window", json={"msgs_per_min": -1}).status_code == 422


def test_window_generator_shapes():
    assert make_windows(10, 1, "normal").shape == (10, len(FEATURES))
