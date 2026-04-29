# A-Agent

Spring Boot novel agent service.

## Run

1. Set environment variables (minimum required):
   - `DB_PASSWORD`
   - `OPENAI_API_KEY`
   - `NAPCAT_ACCESS_TOKEN`
2. Start app:
   - `mvn spring-boot:run`

Optional environment variables:
- `SERVER_PORT` (default `8080`)
- `DB_URL` (default local mysql on `3307`)
- `DB_USERNAME` (default `root`)
- `OPENAI_BASE_URL`
- `OPENAI_MODEL`
- `QQ_ALLOWED_GROUPS`