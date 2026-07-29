from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "postgresql://analyser:analyser@localhost:5433/analyser_db"
    rabbitmq_host: str = "localhost"
    rabbitmq_user: str = "umt"
    rabbitmq_password: str = "umt"
    log_level: str = "INFO"

    class Config:
        env_file = ".env"


settings = Settings()
