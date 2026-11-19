import os

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from .engine import Engine, train

MODEL_DIR = os.getenv("MODEL_DIR", "models")
if not os.path.exists(os.path.join(MODEL_DIR, "sms.joblib")):
    train(MODEL_DIR)  # first boot safety net; Docker image trains at build time

engine = Engine(MODEL_DIR)
app = FastAPI(title="UCBPS ML Engine", version="1.0.0")


def check_key(x_api_key: str | None = Header(default=None)):
    key = os.getenv("ML_API_KEY")
    if key and x_api_key != key:
        raise HTTPException(status_code=401, detail="Invalid API key")


class SmsIn(BaseModel):
    text: str = Field(max_length=1000)


class WindowIn(BaseModel):
    msgs_per_min: float = Field(ge=0)
    otp_ratio: float = Field(ge=0, le=1)
    distinct_senders: float = Field(ge=0)
    calls_per_hour: float = Field(ge=0)
    repeat_number_ratio: float = Field(ge=0, le=1)
    unrequested_otp_ratio: float = Field(ge=0, le=1)
    sample_texts: list[str] = Field(default_factory=list, max_length=20)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/score/sms", dependencies=[Depends(check_key)])
def score_sms(body: SmsIn):
    return {"spam_probability": round(engine.spam_prob(body.text), 4)}


@app.post("/score/window", dependencies=[Depends(check_key)])
def score_window(body: WindowIn):
    d = body.model_dump()
    texts = d.pop("sample_texts")
    return engine.risk(d, texts)
