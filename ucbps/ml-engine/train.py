"""Train and save the models:  python train.py"""
import json
import sys

from app.engine import train

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "models"
    print(json.dumps(train(out), indent=2))
