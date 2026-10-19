# Exceptionless.Java

[![Build](https://github.com/exceptionless/Exceptionless.Java/actions/workflows/build.yml/badge.svg)](https://github.com/exceptionless/Exceptionless.Java/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.exceptionless/exceptionless-core)](https://central.sonatype.com/namespace/com.exceptionless)

The official [Exceptionless](https://exceptionless.com) client for Java. Report errors, logs, feature
usage, 404s and user sessions from any JVM application, with first-class support for Spring Boot,
Servlet containers, Logback, Log4j 2 and `java.util.logging`.

- **Zero dependencies** in the core library (Java 17+). No logging framework, HTTP client or JSON
  library is forced on your application.
- **Never blocks, never throws.** Events are queued and sent in batches on a single daemon thread.
- **Nothing lost on exit.** Queued events are sent when the JVM shuts down; uncaught exceptions are
  reported automatically. Optionally persist the queue to disk.
- **Server-driven.** Log levels, data exclusions and event filters set in your project settings
  apply instantly, without a redeploy.

## Install

Use the BOM to keep module versions aligned:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.exceptionless</groupId>
      <artifactId>exceptionless-bom</artifactId>
      <version>2.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- Spring Boot apps: this is all you need -->
  <dependency>
    <groupId>com.exceptionless</groupId>
    <artifactId>exceptionless-spring-boot-starter</artifactId>
  </dependency>

  <!-- Everything else: the core client, plus any integrations you use -->
  <dependency>
    <groupId>com.exceptionless</groupId>
    <artifactId>exceptionless-core</artifactId>
  </dependency>
</dependencies>
```

Gradle:

```kotlin
implementation(platform("com.exceptionless:exceptionless-bom:2.0.0"))
implementation("com.exceptionless:exceptionless-core")
```

| Module | Use it for |
| --- | --- |
| `exceptionless-core` | The client. Plain Java, CLI and desktop apps. |
| `exceptionless-spring-boot-starter` | Spring Boot 3 and 4 (MVC and WebFlux): auto-configuration, request capture, Logback. |
| `exceptionless-servlet` | Any Jakarta Servlet 6 container: unhandled errors and 404s with request details. |
| `exceptionless-logback` | Logback appender (SLF4J). |
| `exceptionless-log4j2` | Log4j 2 appender. |
| `exceptionless-jul` | `java.util.logging` handler. |

## Quick start

Get an API key from your project's settings, then:

```java
import com.exceptionless.Exceptionless;

public class App {
  public static void main(String[] args) {
    Exceptionless.startup("YOUR_API_KEY");

    try {
      run();
    } catch (Exception e) {
      Exceptionless.submitException(e);
    }
  }
}
```

That's it: uncaught exceptions are reported too, and queued events are sent before the JVM exits.

You can also leave the key out of your code. `Exceptionless.startup()` reads, in order of precedence,
`exceptionless.*` system properties, `EXCEPTIONLESS_*` environment variables, and an
`exceptionless.properties` file on the class path:

```bash
EXCEPTIONLESS_API_KEY=your-key EXCEPTIONLESS_SERVER_URL=https://ex.example.com java -jar app.jar
```

### Spring Boot

Add `exceptionless-spring-boot-starter` and set your API key:

```yaml
exceptionless:
  api-key: ${EXCEPTIONLESS_API_KEY}
  # server-url: https://exceptionless.example.com   # self hosted
  # use-sessions: true
  # storage-path: /var/lib/myapp/exceptionless     # persist queued events across restarts
  # logging:
  #   minimum-level: warn                            # trace, debug, info, warn, error, fatal or off
```

Unhandled controller exceptions (MVC and WebFlux) and 404s are reported with the request details,
Logback warnings and errors are sent as log events, and the `ExceptionlessClient` bean is available
for injection. Customize the configuration with an `ExceptionlessConfigurationCustomizer` bean.

The starter only adds the Logback appender (when Logback is the logging system). With Log4j 2, add
`exceptionless-log4j2` and its `<Exceptionless />` appender to your `log4j2.xml` yourself; without
an `apiKey` it sends through the starter's client. If Logback reloads its configuration
(`scan="true"`), the appender is added again; if your `logback-spring.xml` declares its own
`ExceptionlessAppender`, the starter leaves the root logger alone.

## Sending events

```java
// Errors (any Throwable, including its causes and suppressed exceptions)
Exceptionless.submitException(e);

// Logs, with an optional source (usually the logger name) and level
Exceptionless.submitLog("Order service started");
Exceptionless.submitLog("com.example.OrderService", "Payment retry", LogLevel.WARN);

// Feature usage and 404s
Exceptionless.submitFeatureUsage("Export to CSV");
Exceptionless.submitNotFound("/products/42");
```

Every `create*` method returns a fluent builder for adding context:

```java
Exceptionless.createException(e)
    .addTags("checkout", "payments")
    .setProperty("order", order)                 // any object; serialized via its getters
    .setUserIdentity(user.getId(), user.getName())
    .setUserDescription(user.getEmail(), "I clicked Pay twice")
    .setReferenceId("order-" + order.getId())    // look the event up later
    .setManualStackingKey("payment-declined")    // group these yourself
    .setGeo(43.65, -96.75)
    .setValue(order.getTotal())
    .markAsCritical()
    .submit();
```

Show users a reference id they can quote to support, and attach their description later:

```java
String id = Exceptionless.getLastReferenceId();
Exceptionless.getDefault().updateUserEmailAndDescription(id, "ann@example.com", "It broke");
```

## Configuration

```java
Exceptionless.startup(config -> config
    .apiKey("YOUR_API_KEY")
    .serverUrl("https://exceptionless.example.com")  // self hosted
    .version("1.4.2")                                // or read from your jar's manifest
    .addDefaultTags("backend")
    .addDefaultData("region", "us-east-1")
    .userIdentity("service-account")                 // desktop/CLI apps
    .addDataExclusions("*password*", "*secret*")      // never sent, at any depth
    .includePrivateInformation(false)                // no IPs, machine name, headers, cookies...
    .useFileStorage(Path.of("/var/lib/myapp/exceptionless"))  // survive restarts
    .useSessions());
```

| Option | Default | |
| --- | --- | --- |
| `apiKey` | | Required. |
| `serverUrl` | `https://collector.exceptionless.io` | Also sets the config and heartbeat URLs. |
| `version` | jar `Implementation-Version` | Sent as `@version`. |
| `enabled` | `true` | |
| `includePrivateInformation` | `true` | Master switch; `includeHeaders`, `includeCookies`, `includeIpAddress`, ... individually. |
| `addDataExclusions` | | Wildcards (`*password*`). Merged with the project's `@@DataExclusions`. |
| `addUserAgentBotPatterns` | | Wildcards; events from matching requests are discarded. |
| `defaultMinLogLevel` | none | Minimum log level when no `@@log` setting matches. |
| `useReferenceIds` | `true` | Give each error a reference id. |
| `useSessions` | off | Session start/end and heartbeats. |
| `useFileStorage` | in memory | Persist queued events and settings. |
| `submissionBatchSize` | 50 | |
| `queueProcessInterval` | 10s | |
| `queueMaxAttempts` / `queueMaxAge` | 3 / 2 days | |
| `captureUncaughtExceptions` | `true` | Default uncaught exception handler (chains to any existing one). |
| `registerShutdownHook` | `true` | Send queued events on JVM exit. |
| `proxy`, `sslContext`, `httpClient` | | Network customization. |

### Project settings

These settings in your project's **Settings → Client Configuration** apply to running applications
within a couple of minutes:

| Setting | Example | Effect |
| --- | --- | --- |
| `@@log:<logger>` | `@@log:com.example.*` = `info` | Minimum level for log events (wildcards; most specific wins). |
| `@@error:<type>` | `@@error:java.io.IOException` = `false` | Discard errors of that type (anywhere in the cause chain). |
| `@@<type>:<source>` | `@@404:/favicon.ico` = `false` | Discard other event types by source. |
| `@@DataExclusions` | `*password*,ssn` | Extra data exclusions. |
| `@@IncludePrivateInformation` | `false` | Turn off private information. |
| `@@UserAgentBotPatterns` | `*bot*,*crawler*` | Ignore requests from bots. |

## Logging integrations

Logging integrations send warnings and errors (configurable; the project's `@@log` settings take
precedence). Without their own `apiKey` they use the default client started by
`Exceptionless.startup()`.

**Logback** (`exceptionless-logback`):

```xml
<appender name="EXCEPTIONLESS" class="com.exceptionless.logback.ExceptionlessAppender">
  <minimumLevel>warn</minimumLevel>
</appender>
<root level="INFO">
  <appender-ref ref="EXCEPTIONLESS" />
</root>
```

**Log4j 2** (`exceptionless-log4j2`):

```xml
<Appenders>
  <Exceptionless name="Exceptionless" minimumLevel="warn" />
</Appenders>
<Loggers>
  <Root level="info"><AppenderRef ref="Exceptionless" /></Root>
</Loggers>
```

**java.util.logging** (`exceptionless-jul`):

```properties
handlers=java.util.logging.ConsoleHandler, com.exceptionless.jul.ExceptionlessHandler
com.exceptionless.jul.ExceptionlessHandler.level=WARNING
```

MDC / ThreadContext values (and Log4j `MapMessage` fields) become event properties, markers become
tags, and logged exceptions become error events. Appending never throws: the first failure after
the appender starts is reported to the framework's status output (Logback status, Log4j's error
handler, JUL's `ErrorManager`). Log4j 2 appenders with `ignoreExceptions="false"` also rethrow
failures as `AppenderLoggingException`, for `Failover` appenders.

To integrate another logging framework, build on `com.exceptionless.logging.LogEventSupport` (in
`exceptionless-core`): it implements the client selection (explicit client, a client owned for an
`apiKey`, or the started default client), the filtering of the client's own diagnostics and of
recursive logging, the level checks and the error/log event creation the bundled integrations share.

## Servlet containers

```java
// Programmatically, or declare it in web.xml
servletContext.addFilter("exceptionless", new ExceptionlessFilter()).addMappingForUrlPatterns(null, false, "/*");
```

Unhandled exceptions and 404s are reported with the request (method, path, headers, cookies, query
string, form fields, user) after your privacy settings and data exclusions are applied. Errors logged
while handling a request get the request details too.

Without a client (`new ExceptionlessFilter()`), the filter looks up the default client on every
request and only uses it once `Exceptionless.startup(...)` has run; until then requests pass through
untouched. Pass a client (`new ExceptionlessFilter(client)`) to report to a specific one.

## Plugins and hooks

```java
config.addPlugin("tenant", 15, ctx -> ctx.getEvent().addTags(Tenant.current()));

client.addSubmittingEventHandler(e -> {
  if (e.getEvent().getMessage() != null && e.getEvent().getMessage().contains("healthcheck")) {
    e.cancel();
  }
});

config.addEventExclusion(event -> event.isNotFound() && event.getSource().endsWith(".map"));
```

## Upgrading from 0.1

2.0 is a rewrite; see [MIGRATION.md](MIGRATION.md).

## Samples

- [`samples/console`](samples/console): a plain Java app.
- [`samples/spring-boot`](samples/spring-boot): a Spring Boot web app.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Issues and pull requests are welcome!

## License

[Apache 2.0](LICENSE)
