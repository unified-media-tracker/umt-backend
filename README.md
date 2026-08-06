# umt-backend

Backend for Unified Media Tracker: a Kotlin/Spring Boot microservice backend behind an API
gateway, plus a Python analytics service that scores release-delay probability from news
signals.

| Module | Stack | Role |
|---|---|---|
| `api-gateway` | Kotlin, Spring Cloud Gateway, WebFlux | Single entry point, Keycloak OAuth2/JWT enforcement |
| `core-service` | Kotlin, Spring Boot, JPA, PostgreSQL | Catalog, users, reviews; imports from TMDb / IGDB / MusicBrainz / Metacritic |
| `shared` | Kotlin | Cross-service pieces (Keycloak role converter, MapStruct config) |
| `open-api` | OpenAPI generator | Contract-first API types generated from `umt-api.yaml` |
| `ai-analyser` | Python, FastAPI, SQLAlchemy | Consumes `media.imported`, scores delay probability via a local LLM |

Services talk over a RabbitMQ topic exchange (`umt.events`).

## Running the tests

```bash
./gradlew test
```

`CoreServiceApplicationTests` boots the whole application against a throwaway PostgreSQL
container via Testcontainers, runs the Flyway migration against it, and has Hibernate validate
every JPA entity against the resulting schema (`ddl-auto=validate`). It needs Docker running —
without it the test is skipped rather than failed, so the rest of the suite still works.

Python tests mock the LLM, RabbitMQ, and the database, so they need nothing external:

```bash
cd ai-analyser
pip install -r requirements-dev.txt
pytest
```

## Coverage

```bash
./gradlew test jacocoTestReport      # per-module XML under build/reports/jacoco/
cd ai-analyser && pytest --cov=app --cov-report=term-missing
```

## Versions

Plugin and toolchain versions live in `gradle.properties` and are resolved through
`pluginManagement` in `settings.gradle.kts`, so no module can drift from the others.

One thing worth knowing before touching `shared`: the Spring Boot Gradle plugin is applied with
`apply false` there, which means Boot's automatic alignment of `kotlin.version` never runs and
the BOM would otherwise pull Kotlin back to the version Boot pins. `shared/build.gradle.kts`
overrides `kotlin.version` explicitly for that reason.

## CI

`.github/workflows/ci.yml` runs on every push and pull request:

- **JVM job** — Gradle tests (Testcontainers included), then SonarQube Cloud analysis
- **Python job** — pytest with coverage

### SonarQube Cloud setup

The analysis step is skipped unless a `SONAR_TOKEN` secret exists, so CI stays green until this
is done. To enable it:

None of this depends on the workflow file being pushed first — the token is issued independently.

1. Sign in at [sonarcloud.io](https://sonarcloud.io) with GitHub and import this repository.
   Public repositories are analysed for free with no line limit.
2. Generate the token. On the Free plan this is a **personal** token, not a project one:
   avatar (top right) → **My Account** → **Security** → **Generate Tokens**
   ([sonarcloud.io/account/security](https://sonarcloud.io/account/security)). Copy it
   immediately — it is shown once.
3. Add it to this repository under *Settings → Secrets and variables → Actions → New repository
   secret*, named `SONAR_TOKEN`.
4. Turn off automatic analysis: open the project → **Administration** → **Analysis Method** →
   disable **Automatic Analysis**. The Gradle scanner in CI replaces it, and leaving both on
   makes the CI analysis fail.
5. Check that the project key and organization on SonarQube Cloud match the defaults in the
   root `build.gradle.kts`. If they differ, either edit them there or set `SONAR_PROJECT_KEY` /
   `SONAR_ORGANIZATION` as repository variables.

Coverage reaches Sonar from both sides: JaCoCo XML per Gradle module (picked up automatically
by the scanner) and `ai-analyser/coverage.xml` from pytest, wired up in the root
`build.gradle.kts` under `sonar { properties { ... } }`.
