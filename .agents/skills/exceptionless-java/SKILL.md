---
name: exceptionless-java
description: Use this skill when a developer wants to install, configure, troubleshoot, or integrate the Exceptionless Java client in plain Java, Spring Boot (MVC or WebFlux), Jakarta Servlet containers, Logback/SLF4J, Log4j 2, or java.util.logging apps. Use it for API keys, startup, self-hosting, sending errors/logs/feature usage/404/custom events, custom properties, sessions, heartbeats, user identity, PII/data exclusions, plugins, project client configuration values (@@log, @@error, @@DataExclusions), queues, persistence, and shutdown behavior, even if they only ask "how do I wire up Exceptionless in Java?"
---

# Exceptionless Java Client

Produce source-accurate setup code and guidance for the Exceptionless Java client (`com.exceptionless:*`,
version 2.x, Java 17+). Keep answers compact and copyable, with imports.

## Pick the module

| App | Dependency | Setup |
| --- | --- | --- |
| Spring Boot 3 or 4 | `exceptionless-spring-boot-starter` | `exceptionless.api-key` property; inject `ExceptionlessClient` |
| Servlet container (Tomcat, Jetty...) | `exceptionless-servlet` | `ExceptionlessFilter` + `Exceptionless.startup(...)` (the filter passes requests through until the default client has started) |
| Plain Java / CLI / desktop | `exceptionless-core` | `Exceptionless.startup("KEY")` |
| Logback / SLF4J | `exceptionless-logback` | `com.exceptionless.logback.ExceptionlessAppender` |
| Log4j 2 | `exceptionless-log4j2` | `<Exceptionless name="..." />` appender |
| java.util.logging | `exceptionless-jul` | `com.exceptionless.jul.ExceptionlessHandler` |

Always recommend importing `com.exceptionless:exceptionless-bom` to align versions.

## Rules

- Start once: `Exceptionless.startup("KEY")` or `Exceptionless.startup(c -> c.apiKey(...)...)`. With no
  arguments it reads `exceptionless.*` system properties, `EXCEPTIONLESS_*` environment variables
  (`EXCEPTIONLESS_API_KEY`, `EXCEPTIONLESS_SERVER_URL`) and `exceptionless.properties`.
- In Spring Boot, never call `Exceptionless.startup`; the starter creates the client bean and makes it
  the default. Customize with an `ExceptionlessConfigurationCustomizer` bean.
  `exceptionless.logging.minimum-level` is a `LogLevel` (`trace` ... `off`, any case; aliases such as
  `warning` are rejected) and `exceptionless.storage-path` a directory path.
- The starter only wires Logback. For Log4j 2, add `exceptionless-log4j2` and the `<Exceptionless />`
  appender to `log4j2.xml`; without `apiKey` it uses the starter's client.
- `serverUrl(...)` also sets the config and heartbeat URLs; override those after it.
- The API key, server URLs, storage path, proxy and TLS settings are locked once the client starts.
- `submitException` / `createException` take any `Throwable`; causes and suppressed exceptions are included.
- Builders end with `.submit()`. Submitting never blocks on the network and never throws.
- Short-lived jobs: queued events are sent on JVM shutdown automatically; call
  `Exceptionless.flush(Duration.ofSeconds(10))` when the process may be killed abruptly.
- Logging appenders send `warn` and above by default (`minimumLevel`); the project's `@@log:<logger>`
  settings override that in both directions. Appenders without `apiKey` use the default client once it
  has started; messages logged earlier are not sent.
- Reference ids must be 8-100 characters of letters, digits and dashes; error events get one automatically.
- Use lowercase log levels in settings: `trace`, `debug`, `info`, `warn`, `error`, `fatal`, `off`.
- Don't put application loggers under the `com.exceptionless` package; the integrations treat those as
  the client's own diagnostics and skip them.
- For another logging framework, build the appender on `com.exceptionless.logging.LogEventSupport`
  (client selection, owned client lifecycle, recursion guard, level checks, event creation).

## Privacy

Private information (machine name, IPs, headers, cookies, query strings, form data, user names) is on by
default. In production examples that collect request or user data, show `includePrivateInformation(false)`
or the specific toggles, and `addDataExclusions("*password*", "*secret*", "*token*")`. Exclusions are
wildcards, case-insensitive, applied at every depth. `Authorization`, `Cookie` and similar credential
headers are never sent.

## Project settings (client configuration values)

| Key | Example | Effect |
| --- | --- | --- |
| `@@log:<logger>` | `@@log:com.example.*` = `info` | Minimum log level; most specific wildcard wins |
| `@@error:<exception type>` | `@@error:java.io.IOException` = `false` | Discard errors with that type in the cause chain |
| `@@<type>:<source>` | `@@404:/favicon.ico` = `false` | Discard usage/404/custom events by source |
| `@@DataExclusions` | `*password*,ssn` | Extra exclusions |
| `@@IncludePrivateInformation` | `false` | Turn private info off |
| `@@UserAgentBotPatterns` | `*bot*` | Ignore bot requests |

## Troubleshooting

- Nothing arrives: check `Exceptionless.getConfiguration().validate()` (empty when valid) and
  `isEnabled()`. A missing/invalid key disables the client with a warning instead of throwing.
- Enable client diagnostics: the client logs through `System.Logger` under `com.exceptionless`
  (routed to SLF4J/Log4j/JUL by whatever bridge the app uses). Set that logger to `DEBUG`.
- Self-hosted behind a redirect (http → https): the client reports redirects instead of following them;
  use the final URL.
- Events dropped with HTTP 402: the organization is over its plan limit; the client pauses for an hour.

## Source anchors

Verify behavior in:

- `exceptionless-core/src/main/java/com/exceptionless/Exceptionless.java` (static default client)
- `exceptionless-core/src/main/java/com/exceptionless/ExceptionlessClient.java`
- `exceptionless-core/src/main/java/com/exceptionless/ExceptionlessConfiguration.java`
- `exceptionless-core/src/main/java/com/exceptionless/EventBuilder.java`
- `exceptionless-core/src/main/java/com/exceptionless/plugins/` (pipeline, exclusions, privacy)
- `exceptionless-core/src/main/java/com/exceptionless/queue/DefaultEventQueue.java` (status handling)
- `exceptionless-spring-boot-starter/src/main/java/com/exceptionless/spring/boot/`
- `exceptionless-servlet/src/main/java/com/exceptionless/servlet/ExceptionlessFilter.java`
- `exceptionless-core/src/main/java/com/exceptionless/logging/LogEventSupport.java` (shared appender logic)
- The appenders in `exceptionless-logback`, `exceptionless-log4j2`, `exceptionless-jul`
- `README.md` and `samples/`
