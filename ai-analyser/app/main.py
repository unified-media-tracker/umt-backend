from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.common.config import settings
import threading

from app.common.logging_config import configure_logging
from app.messaging.consumer import start_consumer

configure_logging(settings.log_level)


@asynccontextmanager
async def lifespan(app: FastAPI):
    thread = threading.Thread(target=start_consumer, daemon=True)
    thread.start()
    yield


app = FastAPI(lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok"}
