# whatsapp-bot-final

Full chat-only WhatsApp bot (Indore Nagar Nigam).

This repository implements a WhatsApp chat flow (language selection, OTP verification, grievance registration and tracking, feedback) plus integrations with Digit (PGR) APIs to fetch real grievance data.

High level features
- Language selection (English / Hindi)
- Conversation state stored in Redis (TTL 15 minutes)
- Audit logs for incoming/outgoing messages persisted to Postgres
- OTP verification wiring (integrates with Digit OTP endpoints)
- Grievance listing and tracking using Digit PGR search API (real data)
- Interactive WhatsApp list messages (built from Digit serviceRequestId list)
- Feedback collection and storage
- Config-driven external endpoints and credentials (application.properties)
- SLF4J logging added for observability

Project layout (important files)
- `src/main/java/in/indore/whatsappbot/` — application sources
  - `controller/` — REST controllers (WhatsApp webhook, Digit user endpoints)
  - `service/` — core services: chat flow, WhatsApp message builder, Digit integrations (`GrievanceService`, `DigitUserService`), audit, state, etc.
  - `dto/` — lightweight DTOs used in messaging and Digit responses
  - `model/`, `repository/` — JPA entities and repositories
- `src/main/resources/` — application.properties and i18n message bundles (`messages_en.properties`, `messages_hi.properties`)

Configuration (application.properties)
- Database / Redis (update as needed)
  - `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`
  - `spring.redis.host`, `spring.redis.port`

- WhatsApp
  - `whatsapp.webhook-verify-token` — verification token expected by the webhook
  - `whatsapp.api-token` — API/token used to send messages (if applicable)
  - `whatsapp.api-base-url` — outgoing WhatsApp API base URL (the service will POST raw message JSON to this URL)

- Digit / PGR integration
  - `digit.tenantId` — tenant id used for PGR/Digit calls (example: `mp.indore`)
  - `digit.search.url` — PGR search endpoint (default: `https://urbanimcdev.eydemoapp.in/pgr-services/v2/request/_search`)
  - `digit.token.url` — Digit OAuth token endpoint used by `GrievanceService` (default: `https://urbanimcdev.eydemoapp.in/user/oauth/token`)
  - `digit.token.basic` — base64(clientId:clientSecret) used by token call
  - `digit.token.username`, `digit.token.password` — resource-owner credentials used by the service (if configured)

- Digit UI proxy endpoints (used by the exposed local controller endpoints)
  - `digit.otp.url` — Digit OTP endpoint used by `DigitUserService` (default local UI: `http://localhost:3000/user-otp/v1/_send`)
  - `digit.oauth.url` — Digit OAuth endpoint used by `DigitUserService` (default local UI: `http://localhost:3000/user/oauth/token`)

Run (development)
1. Start required services:
   - Postgres (create database `whatsapp_bot` and apply schema/migrations)
   - Redis
2. Update DB credentials in `src/main/resources/application.properties`.
3. Build and run the app:

```bash
mvn clean package
mvn spring-boot:run
```

HTTP endpoints (key)
- WhatsApp webhook (existing): `POST /api/whatsapp/webhook` — receives WhatsApp-like JSON payloads. The chat flow is processed by `ChatFlowService`.

- Digit user proxy endpoints (exposed locally to mirror Digit UI structure):
  - `POST /user-otp/v1/_send?tenantId=mp` — forwards the OTP request to Digit UI (`digit.otp.url`) and returns:
    - HTTP 201 with `{"message":"OTP created"}` when Digit returns 201
    - Otherwise forwards Digit's response body and HTTP status exactly (useful to surface OTP errors like `OTP.UNKNOWN_CREDENTIAL`).

  - `POST /user/oauth/token` (form-urlencoded) — calls Digit OAuth using the configured Basic auth header (from `digit.token.basic`) and returns:
    - HTTP 200 with `{"message":"User Verified"}` when Digit returns 200 OK
    - Otherwise forwards Digit's response body and HTTP status exactly (e.g. `{"error":"invalid_request","error_description":"Invalid login credentials"}`)

Example: send OTP (curl)
```bash
curl -X POST 'http://localhost:8080/user-otp/v1/_send?tenantId=mp' \
  -H 'Content-Type: application/json' \
  -d '{"otp":{"mobileNumber":"6205434038","tenantId":"mp","userType":"citizen","type":"login"}}'
```
- On failure you will receive Digit's error JSON (e.g. the OTP UNKNOWN_CREDENTIAL error) with the same HTTP status.

Example: token (login) (curl)
```bash
curl -X POST 'http://localhost:8080/user/oauth/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'username=6205434038&password=123456&tenantId=mp&userType=citizen&scope=read&grant_type=password'
```
- On invalid credentials the remote body (e.g. `{"error":"invalid_request","error_description":"Invalid login credentials"}`) is returned to the caller with the same status code.

Grievance listing & tracking
- From the main menu, choosing "2" (Track existing grievance) will call `GrievanceService.getGrievances(phone)` which:
  - obtains a Digit access token (via `digit.token.url`) and calls the configured PGR search API (`digit.search.url`) with `mobileNumber`.
  - returns a list of `serviceRequestId` values.
- The chatbot will build and send an interactive WhatsApp list from those IDs (up to 10 rows). The list rows contain the `serviceRequestId` as the item id so interactive replies contain the ID.
- When the user selects an item (or replies with its id/number), the bot calls `GrievanceService.getGrievanceDetails(serviceRequestId)` which calls the same PGR search API but filtered by `serviceRequestId`. It extracts:
  - `applicationStatus` (Status)
  - `description` (Description)
  - `auditDetails.createdTime` (Date)
  - `auditDetails.lastModifiedTime` (Last update)
  - `workflow.comments` (Remarks)
- The bot renders the `track.details` message template (localized English/Hindi) with these fields and sends it to the user.

Templates & localization
- English messages: `src/main/resources/messages_en.properties`
- Hindi messages: `src/main/resources/messages_hi.properties`
- The `track.details` template is used to present grievance details; the bot substitutes the fields as:
  `track.details=Grievance ID: {0}\nStatus: {1}\nDescription: {2}\nDate: {3}\nLast update: {4}\nRemarks: {5}`
  (Hindi equivalent lives in `messages_hi.properties`)

Logging & errors
- SLF4J logs are added across services (construction, method entry, external API calls, and important branches).
- Digit client errors are forwarded to the caller (both body and HTTP status). This means callers see the exact reason for failures returned by Digit.

Security & secrets
- Keep `digit.token.basic` (base64 client:secret), `digit.token.username` and `digit.token.password` out of source control in production; use environment-specific `application-{profile}.properties` or a secrets manager.

Next improvements (suggestions)
- Token caching: cache Digit access_token in `GrievanceService` until `expires_in` to avoid fetching tokens on every request.
- Configure a `RestTemplate` bean with sensible timeouts and optional retry/backoff for resilience.
- Add integration/unit tests (mocking external Digit endpoints) to assert behavior and error forwarding.

Contact / development notes
- The project is ready for local development; update `application.properties` with real endpoints and credentials, then run.
- If you want, I can add token caching and timeouts next — tell me which to implement.
