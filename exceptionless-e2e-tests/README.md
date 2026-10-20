# Exceptionless end-to-end tests

These tests send events with the Java client to a **real Exceptionless server** and check what the
server stored through its REST API. They prove the client's wire format (snake_case models, error
stacks, request/environment info, sessions, settings, batching and compression) against the server
itself rather than against mocks. This module is not published.

## Running

The tests are skipped unless you enable them, so a normal `mvn verify` doesn't need Docker.

```bash
# Starts the published all-in-one server image with Testcontainers (needs Docker)
mvn -pl exceptionless-e2e-tests verify -De2e=true      # or -Pe2e

# Build exceptionless-core first if it isn't installed in your local repository
mvn -pl exceptionless-e2e-tests -am verify -De2e=true
```

Options (system properties):

| Property | Default | Purpose |
|---|---|---|
| `e2e.image` | `exceptionless/exceptionless:8.10.0` | Server image to start |
| `e2e.serverUrl` | (empty) | Use an already running server (e.g. `http://localhost:7110`) instead of Docker. Use a fresh install: the first user to sign up gets the unlimited plan these tests need. |

The first run pulls about 900 MB. Starting the server takes 30 to 60 seconds. The whole suite takes
about 4 minutes, mostly waiting for the server's session job to close sessions.

## How it works

- `ExceptionlessServer` starts `exceptionless/exceptionless` (built from the server's root
  `Dockerfile`, target `exceptionless`), which runs the API, the background jobs
  (`EX_RunJobsInProcess=true`) and Elasticsearch in one container. It waits for `/ready`, then
  signs up a user (`POST /api/v2/auth/signup`) and creates an organization.
- Each test class creates its own project (`POST /api/v2/projects`) and client API key
  (`POST /api/v2/projects/{id}/tokens` with the `client` scope), so settings changes don't leak.
- Clients use the real `DefaultSubmissionClient`, wrapped by `RecordingSubmissionClient` to record
  every request and response. The raw request bodies are written to `target/e2e/*.json`.
- The server processes events asynchronously, so tests poll `GET /api/v2/projects/{id}/events`,
  `/events/by-ref/{ref}` and `/stacks/{id}` until the data shows up.
- `JsonDiff` compares each event the client sent with what the server stored and fails on any value
  the server dropped or changed. Because the server deserializes `@error`, `@request`,
  `@environment` and friends into typed models and writes them back, a misnamed or mistyped field
  shows up as missing.

## Tests

| Class | Covers |
|---|---|
| `EventWireFormatE2ETest` | Errors (cause chain, suppressed, custom properties, modules, stack frames, signature), environment info, submission client, logs with custom data, feature usage, 404 with request info (plus server-parsed browser and OS), user identity and description, reference ids with `updateUserEmailAndDescription`, manual stacking, a 300-event gzip batch |
| `StackingSignatureE2ETest` | Which frame the server stacks Java errors on, and the `UserNamespaces` setting |
| `SessionE2ETest` | Session start, heartbeat and end, for a user identity and for client-managed session ids |
| `SettingsE2ETest` | Project settings round trip, `@@log`, `@@error`, `@@usage` and `@@DataExclusions` filtering, and refresh triggered by the `X-Exceptionless-ConfigVersion` header |
| `ErrorResponseE2ETest` | Invalid API key (401) and request too large (413) |
