# Contributing

Thanks for helping improve the Exceptionless Java client!

## Building

You need JDK 17 or newer. The Maven wrapper downloads the right Maven version.

```bash
./mvnw verify                         # build and run all tests
./mvnw -pl exceptionless-core verify  # one module
./mvnw spotless:apply                 # format the code
```

End-to-end tests run the client against a real Exceptionless server in Docker. They are skipped
unless you ask for them:

```bash
./mvnw -pl exceptionless-e2e-tests -am verify -De2e=true
```

Test the Spring Boot starter against Spring Boot 4 with `-Pspring-boot-4`.

## Guidelines

- `exceptionless-core` has **no runtime dependencies**. Keep it that way: use the JDK (`java.net.http`,
  `System.Logger`) and the small JSON writer in `com.exceptionless.json`.
- Integrations depend on their framework with `provided` scope so applications choose the version.
- Never throw from a code path that runs inside the application (submitting, logging appenders,
  filters). Log through `com.exceptionless.internal.Log` and carry on.
- Never block the caller on the network. Network calls run on the client's background thread.
- Everything sent to the server must match the server's snake_case models; `WireContractTest` and the
  end-to-end tests guard this.
- Public API gets javadoc. Packages are `@NullMarked` (JSpecify); mark nullable types with `@Nullable`.
- Formatting is google-java-format, enforced by Spotless.
- On JDK 21 and newer the build also runs [Error Prone](https://errorprone.info) and
  [NullAway](https://github.com/uber/NullAway) on the main sources, with warnings as errors. Fix
  findings rather than suppressing them; when a suppression is right, say why next to it.
- Write idiomatic Java 17: records for values, sealed types for closed results, `java.time` types
  (`Duration`, `InstantSource`) instead of numbers, immutable collections from getters, and plain
  JavaBeans (`void` setters) for the mutable models.

## Releasing

Push a tag like `v2.0.0`. The release workflow builds, signs and publishes every module to Maven Central
and creates a GitHub release.
