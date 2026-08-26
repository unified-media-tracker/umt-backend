from pathlib import Path

from pydantic_settings import BaseSettings

ENV_FILE = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    database_url: str
    rabbitmq_password: str
    rabbitmq_host: str = "localhost"
    rabbitmq_user: str = "umt"
    log_level: str = "INFO"
    youtube_api_key: str = ""

    class Config:
        env_file = ENV_FILE


settings = Settings()
