#!/usr/bin/env python3
"""Safe attack SIMULATOR for UCBPS. It never sends SMS or places calls.

It only posts *synthetic threat indicators* (fake 555-01xx numbers, throw-away accounts) to your own UCBPS
backend so the dashboard and the reputation logic can be tested end to end.

  python simulate.py --api http://localhost:8080 --scenario bombing
  python simulate.py --api https://YOUR-BACKEND.onrender.com --scenario all
"""
import argparse
import hashlib
import hmac
import random
import sys
import time

import requests

PEPPER = "ucbps-dev-pepper-v1"  # must match -PUCBPS_PEPPER used for the Android build
COUNTRIES = ["IN", "US", "GB", "BR", "DE", "NG", "ID", "PH"]


def number_hash(number: str) -> str:
    n = "".join(c for c in number if c.isdigit() or c == "+")
    return hmac.new(PEPPER.encode(), n.encode(), hashlib.sha256).hexdigest()


class Client:
    def __init__(self, api: str):
        self.api = api.rstrip("/")

    def user(self, tag: str, consent: bool = True) -> str:
        email = f"sim-{tag}-{random.randint(0, 10**9)}@example.test"
        r = requests.post(f"{self.api}/api/v1/auth/register", timeout=30, json={
            "email": email, "password": "SimPassw0rd!", "country": random.choice(COUNTRIES), "reportingConsent": consent})
        r.raise_for_status()
        return r.json()["token"]

    def report(self, token: str, h: str, country: str, kind: str, risk: float) -> dict:
        r = requests.post(f"{self.api}/api/v1/reports", timeout=30,
                          headers={"Authorization": f"Bearer {token}"},
                          json={"numberHash": h, "country": country, "type": kind, "riskScore": risk})
        r.raise_for_status()
        return r.json()


def bombing(c: Client, devices: int = 5):
    """Many independent devices all see the same sender flood them -> number becomes flagged."""
    h = number_hash("+15550100001")
    print(f"[bombing] {devices} devices report the same synthetic number")
    for i in range(devices):
        res = c.report(c.user("bomb"), h, random.choice(COUNTRIES), "OTP_FLOOD", round(random.uniform(0.8, 0.98), 2))
        print(f"  device {i + 1}: flagged={res['flagged']} score={res['score']:.2f}")


def noisy_single_user(c: Client):
    """One user reports a number 10 times. Must NEVER become flagged (frequency alone is not malicious)."""
    h = number_hash("+15550100002")
    t = c.user("noisy")
    print("[noisy] one device reports the same number 10 times")
    for _ in range(10):
        res = c.report(t, h, "IN", "SMS_FLOOD", 0.9)
    print(f"  final: flagged={res['flagged']}  (expected False)")


def background(c: Client, n: int = 25):
    print(f"[background] {n} random low-volume indicators")
    users = [c.user("bg") for _ in range(6)]
    kinds = ["SMS_FLOOD", "OTP_FLOOD", "CALL_FLOOD", "SPAM_SMS"]
    for i in range(n):
        h = number_hash(f"+1555010{random.randint(100, 999)}")
        c.report(random.choice(users), h, random.choice(COUNTRIES), random.choice(kinds), round(random.uniform(0.4, 0.95), 2))


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--api", default="http://localhost:8080")
    p.add_argument("--scenario", choices=["bombing", "noisy", "background", "all"], default="all")
    a = p.parse_args()
    c = Client(a.api)
    try:
        if a.scenario in ("bombing", "all"): bombing(c)
        if a.scenario in ("noisy", "all"): noisy_single_user(c)
        if a.scenario in ("background", "all"): background(c)
    except requests.RequestException as e:
        sys.exit(f"Request failed: {e}\nIs the backend running at {a.api}? Free hosts can take ~1 minute to wake up.")
    print("Done. Open the dashboard to see the synthetic incidents.")


if __name__ == "__main__":
    main()
