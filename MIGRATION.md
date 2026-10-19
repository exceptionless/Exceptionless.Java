# Migrating from 0.1 to 2.0

2.0 is a rewrite. The 0.1 client sent data in a format the current Exceptionless server no longer
reads (stack traces, environment and request details were dropped), so upgrading is strongly
recommended.

## Dependencies

| 0.1 | 2.0 |
| --- | --- |
| `com.exceptionless:exceptionless-client` | `com.exceptionless:exceptionless-core` (plus integrations, see the README) |
| Java 11 | Java 17 |
| OkHttp, Jackson, Logback, Lombok (transitive) | none |

Remove any OkHttp version pins you added for the old client.

## Packages and types

| 0.1 | 2.0 |
| --- | --- |
| `com.exceptionless.exceptionlessclient.ExceptionlessClient` | `com.exceptionless.ExceptionlessClient` |
| `ExceptionlessClient.from(apiKey, serverUrl)` | `ExceptionlessClient.create(c -> c.apiKey(apiKey).serverUrl(serverUrl))` or `Exceptionless.startup(...)` |
| `Configuration.builder()...build()` | `new ExceptionlessConfiguration().apiKey(...)...` (fluent setters) |
| `client.submitException(Exception)` | `client.submitException(Throwable)` |
| `client.createError()` / `createLog(...)` + `.build()` + `submitEvent(EventPluginContext.from(...))` | `client.createException(e)` / `createLog(...)` + `.submit()` |
| `EventPluginIF` | `com.exceptionless.plugins.EventPlugin`, or `EventPlugin.of(name, priority, ctx -> ...)` for a lambda |
| `EventQueueIF`, `SubmissionClientIF`, `SettingsClientIF`, `StorageProviderIF` | `EventQueue`, `SubmissionClient`, `QueueStorage` / `KeyValueStorage` |
| `PrivateInformationInclusions` | `config.includePrivateInformation(...)`, `includeHeaders(...)`, `includeCookies(...)`, ... |
| `LogCapturerIF` | Use a logging integration (`exceptionless-logback`, `-log4j2`, `-jul`). |

## Behavior changes

- The client never keeps the JVM alive, sends queued events on shutdown, and reports uncaught
  exceptions (turn off with `captureUncaughtExceptions(false)`).
- The client no longer adds an appender to the Logback root logger. Add `ExceptionlessAppender`
  yourself, or use the Spring Boot starter.
- Private information (machine name, IP address, headers, cookies, query strings, form data) is
  included by default, like the other Exceptionless clients. Use
  `includePrivateInformation(false)` to opt out.
- A missing or invalid API key disables the client (with a warning) instead of throwing.
- The API key is sent in the `Authorization` header instead of the query string.
