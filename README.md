# whatsapp-bot-final

A chat-first WhatsApp bot for Indore Nagar Nigam that demonstrates a complete conversation flow: language selection, OTP verification, grievance registration & tracking, and feedback collection. The service integrates with Digit (PGR) APIs to fetch real grievance data and exposes a small local proxy for Digit user endpoints (OTP / OAuth) used during development.

This README documents the project structure, exact configuration keys (from `src/main/resources/application.properties`), quick start (local + Docker), key endpoints, examples, and development notes.

Summary of key features
- Language selection (English / Hindi)
- Conversation state stored in Redis (TTL ~15 minutes)
- Audit logs for incoming/outgoing messages persisted to Postgres
- OTP verification wiring (integrates with Digit OTP endpoints / local proxy)
- Grievance listing & tracking using Digit PGR search API
- Interactive WhatsApp list messages (built from Digit `serviceRequestId` list)
- Feedback collection and storage
- Configuration-driven external endpoints and credentials
- SLF4J logging across services

Project layout (important paths)
- `src/main/java/in/indore/whatsappbot/` — application sources
  - `controller/` — REST controllers (WhatsApp webhook, Digit user proxy endpoints)
  - `service/` — core services: chat flow, WhatsApp message builder, Digit integrations (`GrievanceService`, `DigitUserService`), audit, state, etc.
  - `dto/` — DTOs used in messaging and Digit responses
  - `model/`, `repository/` — JPA entities and Spring Data repositories
- `src/main/resources/` — configuration and localization
  - `application.properties`, `application-local.properties`
  - `messages_en.properties`, `messages_hi.properties` (i18n templates)
  - `db/migration/V1__init.sql` (Flyway migration)
- `docker-compose.yml` — Compose file with Postgres and Redis for local development
- `pom.xml` — Maven build (Java 17, Spring Boot 3.1.x)

Requirements
- Java 17
- Maven 3.x
- Postgres (compose uses Postgres 15)
- Redis

Configuration (exact keys)

The application reads configuration from `src/main/resources/application.properties`. Below are the exact keys present in the file and what they control.

Server & profile
- `spring.profiles.active` — active Spring profile (default in repo: `local`)
- `server.port` — HTTP server port (default: `8080`)

Postgres / JPA
- `spring.datasource.url` — JDBC URL (example in repo: `jdbc:postgresql://localhost:5432/whatsapp_bot`)
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.jpa.hibernate.ddl-auto` — e.g. `validate`
- `spring.jpa.properties.hibernate.jdbc.time_zone` — time zone for JDBC (UTC in repo)
- `spring.jpa.database-platform` — Hibernate dialect (PostgreSQL)

Flyway
- `spring.flyway.enabled` — enable Flyway migrations
- `spring.flyway.locations` — migration locations (repo: `classpath:db/migration`)

Redis
- `spring.redis.host`
- `spring.redis.port`

UTF-8 / message encoding
- `spring.messages.basename` (repo: `messages`)
- `spring.messages.encoding` (UTF-8)
- `spring.http.encoding.*`, `server.servlet.encoding.*` — the repo sets these to force UTF-8 for requests/responses and resources

WhatsApp outgoing/webhook
- `whatsapp.webhook-verify-token` — verification token used when validating webhook calls (default placeholder: `change_me_verify_token`)
- `whatsapp.api-token` — API token used to send outgoing messages (placeholder in `application.properties`)
- `whatsapp.api-base-url` — outgoing WhatsApp API base (example placeholder in repo: `https://graph.facebook.com/v22.0/{{to_set}}/messages`)

Digit / token (OAuth)
- `digit.token.basic` — Basic auth header value (base64 `clientId:clientSecret`) used for token calls
- `digit.token.username` — resource-owner username when using password grant
- `digit.token.password` — resource-owner password
- `digit.token.scope` — token scope (repo: `read`)
- `digit.token.grant_type` — e.g. `password`
- `digit.token.userType` — e.g. `EMPLOYEE`
- `digit.tenantId` — tenant id used for PGR/Digit calls (repo: `mp.indore`)

PGR / Digit endpoints (search/create)
- `egov.pgr.service.host` — host for PGR APIs (repo: `https://urbanimcdev.eydemoapp.in/`)
- `egov.pgr.search.path` — PGR search path (`pgr-services/v2/request/_search`)
- `egov.pgr.create.path` — PGR create path (`pgr-services/v2/request/_create`)

Digit UI local proxy endpoints (OTP / OAuth)
- `egov.user.service.host` — host used for user OTP / OAuth (repo: `https://urbanimcdev.eydemoapp.in/`)
- `egov.user.send-otp.path` — OTP send path (`user-otp/v1/_send`)
- `egov.user.token.path` — user token path (`user/oauth/token`)

Digit UI form links (used by UI or messages)
- `egov.form.host` — host used for form links
- `egov.form.login.path` — path to mobile login UI
- `egov.form.grievance.path` — path to grievance form UI

Notes about mapping and usage
- The application composes full Digit endpoints by concatenating the host and the path keys above. For example, the PGR search full URL is: `${egov.pgr.service.host}${egov.pgr.search.path}`.
- The local proxy endpoints exposed by the app (controllers) follow the Digit UI paths so UI/dev tooling expecting `POST /user-otp/v1/_send` and `POST /user/oauth/token` will work against the app when configured properly.

application-local.properties — local profile notes
- The repository includes `src/main/resources/application-local.properties` which mirrors many keys from `application.properties` but may contain example values used for local development.
- This file in the repo includes sample (non-production) credentials and an example `whatsapp.api-token`. Do not use these values in production.
- Additional keys present in `application-local.properties` not explicitly defined in `application.properties` include:
  - `url.login.form` — a local URL used by the app for login/form links (example: `http://localhost:8080/login/form/`)
  - `url.grievance.form` — a local URL used for the grievance form (example: `http://localhost:8080/grievance/form/`)
- There are also commented-out keys (examples) such as `digit.otp.url`, `digit.token.url`, and `digit.search.url`. These are conveniences for local testing; the app uses the `egov.*` host+path properties listed above.

Security note
- `digit.token.basic`, `digit.token.username`, `digit.token.password`, and `whatsapp.api-token` are sensitive. Avoid committing real production credentials to the repository. Use environment-specific properties, Docker secrets, or a secrets manager in production.
- If `application-local.properties` contains sample credentials you no longer want in the repo, remove or rotate them.

Quick start — Local (Maven)
1. Ensure Postgres and Redis are running. Create database `whatsapp_bot` if using the sample JDBC URL in `application.properties`.
2. Edit `src/main/resources/application-local.properties` or `application.properties` with the correct DB/Redis and Digit credentials for your environment.
3. Build and run:

```bash
mvn clean package
mvn spring-boot:run
```

Quick start — Docker Compose
The included `docker-compose.yml` starts the app image (built from the project), Postgres and Redis, and sets several environment variables expected by Spring Boot (see the compose file for details).

Start the stack:

```bash
docker compose up --build
```

Stop and remove containers:

```bash
docker compose down
```

Environment variables used by `docker-compose.yml` (examples)
- `SPRING_DATASOURCE_URL` (example: `jdbc:postgresql://db:5432/whatsapp_bot`)
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_REDIS_HOST`
- `SPRING_REDIS_PORT`
- `SPRING_JPA_HIBERNATE_DDL_AUTO`
- `SPRING_FLYWAY_ENABLED`

HTTP endpoints — Important ones
- WhatsApp webhook
  - `POST /api/whatsapp/webhook` — receives WhatsApp-like JSON payloads and drives the conversation via `ChatFlowService`.

- Digit user proxy endpoints (exposed locally to mirror Digit UI structure):
  - `POST /user-otp/v1/_send?tenantId={tenantId}`
    - Forwards OTP creation to `${egov.user.service.host}${egov.user.send-otp.path}`. Returns 201 with `{"message":"OTP created"}` when Digit returns 201; otherwise forwards Digit's body & status.

  - `POST /user/oauth/token`
    - Forwards form-urlencoded token requests to `${egov.user.service.host}${egov.user.token.path}` using Basic auth header from `digit.token.basic`. Returns 200 with `{"message":"User Verified"}` when Digit returns 200; otherwise forwards Digit's body & status.

Examples (curl)
- Send OTP

```bash
curl -X POST 'http://localhost:8080/user-otp/v1/_send?tenantId=mp' \
  -H 'Content-Type: application/json' \
  -d '{"otp":{"mobileNumber":"6205434038","tenantId":"mp","userType":"citizen","type":"login"}}'
```

- Token (login)

```bash
curl -X POST 'http://localhost:8080/user/oauth/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'username=6205434038&password=123456&tenantId=mp&userType=citizen&scope=read&grant_type=password'
```

Grievance listing & tracking (how the app uses the config)
- `GrievanceService.getGrievances(phone)` obtains a Digit access token (using `digit.token.*` config) and calls the PGR search endpoint `${egov.pgr.service.host}${egov.pgr.search.path}` with `mobileNumber` to fetch matching requests.
- The bot builds an interactive WhatsApp list (up to 10 rows) where each item id is the `serviceRequestId`. When a user selects an item, the bot calls `GrievanceService.getGrievanceDetails(serviceRequestId)` which re-queries the PGR search API filtered by `serviceRequestId` and extracts fields like `applicationStatus`, `description`, `auditDetails.createdTime`, `auditDetails.lastModifiedTime` and `workflow.comments`.
- The localized template key `track.details` from `messages*.properties` is used to render these fields for the user.

Templates & localization
- English: `src/main/resources/messages_en.properties`
- Hindi: `src/main/resources/messages_hi.properties`
- The property `spring.messages.basename` is set to `messages` so Spring will pick the correct bundle based on locale.

Logging & errors
- SLF4J logging is used throughout the code. External Digit client errors are forwarded to callers (body + HTTP status) so the precise upstream error is preserved.

Development suggestions / next improvements
- Token caching: cache Digit `access_token` until `expires_in` to avoid fetching on every call (in-memory or Redis-backed).
- External call resilience: configure `RestTemplate`/`WebClient` with timeouts and retries/backoff.
- Tests: add integration tests that mock Digit endpoints to assert behavior and error forwarding.
- Add `env` or `.env.example` documenting the most important environment variables for Docker/dev.

Health & troubleshooting
- Check logs for DB connection/credential errors and Redis connectivity.
- If Hindi text appears corrupted, the `pom.xml` enforces UTF-8 and disables filtering for `.properties` files — ensure builds use the same settings.

License
- The repository contains a `LICENSE` file — follow its terms when using or modifying this project.

Next steps I can implement for you
- add a `.env.example` file with the common environment variables used by `docker-compose.yml` (placeholders only, no secrets),
- implement token caching in `GrievanceService`, or
- create a small integration test harness that mocks Digit endpoints and verifies OTP/token flows.

Tell me which of those you'd like me to implement next.
