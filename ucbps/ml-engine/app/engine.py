"""UCBPS detection engine: SMS spam classifier + flood anomaly detector + risk scoring.

Trained on SYNTHETIC data only (see make_sms_dataset / make_windows). Replace the generators with a real,
licensed dataset (e.g. the UCI SMS Spam Collection) and real anonymised windows before production use.
"""
from __future__ import annotations

import random
import re
from pathlib import Path

import joblib
import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline

FEATURES = [
    "msgs_per_min",
    "otp_ratio",
    "distinct_senders",
    "calls_per_hour",
    "repeat_number_ratio",
    "unrequested_otp_ratio",
]

_DIGITS = re.compile(r"\d+")


def normalize(text: str) -> str:
    """Module-level so the pickled pipeline can find it again."""
    return _DIGITS.sub(" <num> ", text.lower())


# ---------------------------------------------------------------- synthetic data
_OTP = [
    "Your OTP is {n}. Do not share it with anyone.",
    "{n} is your verification code for {b}.",
    "Use {n} as your login code. Valid for 5 minutes.",
    "{b}: security code {n}. Never share this code.",
]
_HAM = [
    "Hey, are we still meeting at {n}?",
    "Your order #{n} has been shipped and arrives tomorrow.",
    "Reminder: appointment tomorrow at {n} pm.",
    "Thanks for your payment of Rs {n}. Your balance is updated.",
    "Happy birthday! Hope you have a great day.",
    "Can you send me the notes from class? Thanks.",
    "Lunch at 1? I will book a table.",
]
_SPAM = [
    "URGENT! You have won a free prize of {n}. Click http://bit.ly/{s} to claim now",
    "Your account is blocked. Verify KYC immediately at http://{s}.xyz",
    "Congratulations winner! Claim your lottery reward now {n}",
    "Act now: loan approved Rs {n}, no documents, click http://{s}.top",
    "FREE entry in weekly draw. Reply WIN to {n} to claim your prize",
]
_BRANDS = ["ShopCo", "BankOne", "PayFast", "MailBox", "RideApp"]


def _fill(tpl: str, rng: random.Random) -> str:
    return tpl.format(n=rng.randint(1000, 999999), b=rng.choice(_BRANDS), s="".join(rng.choices("abcdefgh", k=6)))


def make_sms_dataset(n: int = 1500, seed: int = 7):
    rng = random.Random(seed)
    texts, labels = [], []
    for _ in range(n):
        r = rng.random()
        if r < 0.35:
            texts.append(_fill(rng.choice(_SPAM), rng)); labels.append(1)
        elif r < 0.65:
            texts.append(_fill(rng.choice(_OTP), rng)); labels.append(0)   # legitimate OTPs are NOT spam
        else:
            texts.append(_fill(rng.choice(_HAM), rng)); labels.append(0)
    return texts, labels


def make_windows(n: int, seed: int, kind: str) -> np.ndarray:
    """Feature windows. kind: normal | retry | attack. Attack windows exist only to evaluate the detector."""
    g = np.random.default_rng(seed)
    if kind == "normal":
        cols = [g.gamma(1.2, 0.25, n), g.beta(1, 5, n), 1 + g.poisson(1.0, n),
                g.poisson(1.5, n), g.beta(1, 8, n), g.beta(1, 10, n)]
    elif kind == "retry":  # a user hammering "resend OTP" from one service: busy but legitimate
        cols = [g.gamma(3, 0.5, n), g.beta(8, 2, n), np.ones(n),
                g.poisson(1.5, n), g.beta(1, 8, n), g.beta(1, 15, n)]
    elif kind == "attack":
        cols = [g.gamma(8, 1.2, n), g.beta(8, 2, n), 6 + g.poisson(6, n),
                g.poisson(12, n), g.beta(5, 2, n), g.beta(8, 1.5, n)]
    else:
        raise ValueError(kind)
    return np.column_stack(cols)


# ---------------------------------------------------------------- training
def train(out_dir: str = "models", seed: int = 7) -> dict:
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)

    texts, labels = make_sms_dataset(1500, seed)
    xtr, xte, ytr, yte = train_test_split(texts, labels, test_size=0.2, random_state=seed, stratify=labels)
    sms = Pipeline([
        ("tfidf", TfidfVectorizer(preprocessor=normalize, ngram_range=(1, 2), min_df=1)),
        ("clf", LogisticRegression(max_iter=1000)),
    ]).fit(xtr, ytr)
    sms_acc = accuracy_score(yte, sms.predict(xte))

    normal = np.vstack([make_windows(1900, seed, "normal"), make_windows(100, seed + 1, "retry")])
    iso = IsolationForest(n_estimators=200, random_state=seed).fit(normal)
    raw = -iso.score_samples(normal)
    p50, p99 = float(np.percentile(raw, 95)), float(np.percentile(raw, 99.9))  # 0 at p95, 1 at p99.9

    attack = make_windows(300, seed + 2, "attack")
    detected = float(np.mean(_norm(-iso.score_samples(attack), p50, p99) >= 0.5))
    false_alarm = float(np.mean(_norm(raw, p50, p99) >= 0.5))

    joblib.dump(sms, out / "sms.joblib")
    joblib.dump({"iso": iso, "p50": p50, "p99": p99}, out / "anomaly.joblib")
    return {"sms_accuracy": round(sms_acc, 4), "attack_detection_rate": round(detected, 4),
            "normal_false_alarm_rate": round(false_alarm, 4)}


def _norm(raw, p50: float, p99: float):
    return np.clip((raw - p50) / max(p99 - p50, 1e-9), 0.0, 1.0)


# ---------------------------------------------------------------- inference
class Engine:
    def __init__(self, model_dir: str = "models"):
        d = Path(model_dir)
        self.sms = joblib.load(d / "sms.joblib")
        a = joblib.load(d / "anomaly.joblib")
        self.iso, self.p50, self.p99 = a["iso"], a["p50"], a["p99"]

    def spam_prob(self, text: str) -> float:
        return float(self.sms.predict_proba([text])[0][1])

    def anomaly(self, w: dict) -> float:
        x = np.array([[float(w[f]) for f in FEATURES]])
        return float(_norm(-self.iso.score_samples(x), self.p50, self.p99)[0])

    def risk(self, w: dict, texts: list[str] | None = None) -> dict:
        """Frequency alone can never produce a 'suspicious' verdict: at least two independent signals are required."""
        anomaly = self.anomaly(w)
        spam = max((self.spam_prob(t) for t in texts), default=0.0) if texts else 0.0
        unreq = float(w["unrequested_otp_ratio"])
        signals = [anomaly >= 0.5, spam >= 0.5, unreq >= 0.5, float(w["distinct_senders"]) >= 5]
        score = 0.5 * anomaly + 0.25 * spam + 0.25 * unreq
        n = sum(signals)
        if n < 2:
            score = min(score, 0.49)
        label = "suspicious" if score >= 0.6 else "watch" if score >= 0.35 else "normal"
        return {"risk": round(score, 4), "label": label, "signals": n,
                "anomaly": round(anomaly, 4), "spam": round(spam, 4)}
