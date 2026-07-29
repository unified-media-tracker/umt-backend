from fastapi import FastAPI
from app.config import settings
import threading
from app.messaging.consumer import start_consumer

app = FastAPI()


@app.on_event("startup")
def startup_event():
    # Start a RabbitMQ consumer in a separate thread
    thread = threading.Thread(target=start_consumer, daemon=True)
    thread.start()


@app.get("/health")
def health():
    return {"status": "ok"}
