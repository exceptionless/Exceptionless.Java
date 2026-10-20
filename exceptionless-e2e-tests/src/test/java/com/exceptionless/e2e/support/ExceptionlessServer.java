package com.exceptionless.e2e.support;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * The Exceptionless server under test, started once per test JVM.
 *
 * <p>By default this runs the published all-in-one image ({@code exceptionless/exceptionless},
 * built from the server's root {@code Dockerfile} target {@code exceptionless}): the API, the
 * background jobs ({@code EX_RunJobsInProcess=true}) and Elasticsearch in one container, with an
 * in-memory queue/cache. Set {@code -De2e.serverUrl=http://host:port} to test an already running
 * server instead (for example one started from the server repo with Aspire).
 *
 * <p>A single account (user, organization) is created on first use; tests create their own projects
 * so settings changes can't leak between them.
 */
public final class ExceptionlessServer {
  private static final Logger LOG = LoggerFactory.getLogger(ExceptionlessServer.class);
  private static final int HTTP_PORT = 8080;
  private static ExceptionlessServer instance;

  private final GenericContainer<?> container;
  private final ServerApi api;
  private final String organizationId;

  private ExceptionlessServer(GenericContainer<?> container, String baseUrl) {
    this.container = container;
    this.api = new ServerApi(baseUrl);
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    api.signup("E2E Tester", "e2e-" + suffix + "@example.com", "E2e-Password-" + suffix);
    this.organizationId = api.createOrganization("Java E2E " + suffix);
    LOG.info("Exceptionless server ready at {} (organization {})", baseUrl, organizationId);
  }

  /** Starts the server (once) and bootstraps an account. */
  public static synchronized ExceptionlessServer get() {
    if (instance == null) {
      String serverUrl = System.getProperty("e2e.serverUrl", "");
      if (!serverUrl.isBlank()) {
        instance = new ExceptionlessServer(null, serverUrl.trim());
      } else {
        instance = startContainer();
      }
    }
    return instance;
  }

  @SuppressWarnings("resource") // stopped by the shutdown hook below (and by Testcontainers' Ryuk)
  private static ExceptionlessServer startContainer() {
    String image = System.getProperty("e2e.image", "exceptionless/exceptionless:8.10.0");
    LOG.info(
        "Starting {} (first start pulls ~900 MB and boots Elasticsearch; allow a few minutes)",
        image);
    GenericContainer<?> container =
        new GenericContainer<>(DockerImageName.parse(image))
            .withExposedPorts(HTTP_PORT)
            .withEnv("EX_AppMode", "Production")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx1g")
            .withLogConsumer(
                new Slf4jLogConsumer(LoggerFactory.getLogger("exceptionless-server"))
                    .withSeparateOutputStreams())
            // /ready only succeeds once Elasticsearch, storage and the startup actions are healthy.
            .waitingFor(
                Wait.forHttp("/ready")
                    .forPort(HTTP_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(5)));
    container.start();
    Runtime.getRuntime()
        .addShutdownHook(new Thread(container::stop, "exceptionless-e2e-server-stop"));
    String baseUrl = "http://" + container.getHost() + ":" + container.getMappedPort(HTTP_PORT);
    // The startup actions (index creation) gate requests; wait for the API itself to answer.
    Await.until(
        "the API to answer",
        Duration.ofMinutes(2),
        () -> new ServerApi(baseUrl).send("GET", "/api/v2/about", null, null).status() == 200);
    return new ExceptionlessServer(container, baseUrl);
  }

  public ServerApi api() {
    return api;
  }

  public String baseUrl() {
    return api.baseUrl();
  }

  public String organizationId() {
    return organizationId;
  }

  /** The container's log so far (empty when testing an external server). */
  public String logs() {
    return container == null ? "" : container.getLogs();
  }

  /** Creates a project with a client API key. */
  public TestProject createProject(String name) {
    String projectId =
        api.createProject(
            organizationId, name + " " + UUID.randomUUID().toString().substring(0, 6));
    String apiKey = api.createClientToken(projectId);
    return new TestProject(this, projectId, apiKey);
  }
}
