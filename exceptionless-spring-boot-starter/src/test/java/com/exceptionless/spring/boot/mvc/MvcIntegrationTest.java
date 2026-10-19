package com.exceptionless.spring.boot.mvc;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.spring.boot.FakeServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Runs a real Spring MVC application on Tomcat and checks the events that reach the server. */
@SpringBootTest(
    classes = MvcTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MvcIntegrationTest {
  private static final FakeServer SERVER = startServer();
  private final HttpClient http =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

  @Autowired private ExceptionlessClient client;
  @Autowired private Environment environment;

  @DynamicPropertySource
  static void exceptionlessProperties(DynamicPropertyRegistry registry) {
    registry.add("exceptionless.api-key", () -> "LhhP1C9gijpSKCslHHCvwdSIz298twx271nTest");
    registry.add("exceptionless.server-url", SERVER::url);
    registry.add("exceptionless.capture-uncaught-exceptions", () -> "false");
    registry.add("exceptionless.default-tags", () -> "mvc-test");
  }

  @AfterAll
  static void stopServer() {
    SERVER.close();
  }

  @Test
  void reportsControllerExceptionWithRequestInfoOnce() throws Exception {
    HttpResponse<String> response =
        get("/boom?order=42", "Authorization", "Bearer secret", "X-Trace", "abc");
    assertThat(response.statusCode()).isEqualTo(500);

    Map<String, Object> event = awaitEvent(e -> "controller boom".equals(error(e).get("message")));
    assertThat(event).containsEntry("type", "error");
    assertThat(event).containsEntry("tags", List.of("mvc-test"));
    assertThat(error(event)).containsEntry("type", "java.lang.IllegalStateException");
    assertThat(data(event)).containsEntry("@submission_method", "servletFilter");
    Map<String, Object> request = request(event);
    assertThat(request)
        .containsEntry("http_method", "GET")
        .containsEntry("path", "/boom")
        .containsEntry("query_string", Map.of("order", "42"));
    @SuppressWarnings("unchecked")
    Map<String, Object> headers = (Map<String, Object>) request.get("headers");
    assertThat(headers.keySet())
        .noneMatch(name -> name.equalsIgnoreCase("authorization"))
        .anyMatch(name -> name.equalsIgnoreCase("x-trace"));

    // Tomcat logs the same exception through Logback; it must not be reported again.
    client.flush(Duration.ofSeconds(5));
    assertThat(events())
        .filteredOn(e -> "controller boom".equals(error(e).get("message")))
        .hasSize(1);
  }

  @Test
  void reportsNotFound() throws Exception {
    assertThat(get("/does-not-exist").statusCode()).isEqualTo(404);

    Map<String, Object> event =
        awaitEvent(e -> "404".equals(e.get("type")) && "/does-not-exist".equals(e.get("source")));
    assertThat(data(event)).containsEntry("@submission_method", "servletFilter");
    assertThat(request(event)).containsEntry("path", "/does-not-exist");
  }

  @Test
  void addsRequestInfoToErrorsLoggedDuringTheRequest() throws Exception {
    assertThat(get("/logged", "User-Agent", "IntegrationTest/1.0").statusCode()).isEqualTo(200);

    Map<String, Object> event = awaitEvent(e -> "logged boom".equals(error(e).get("message")));
    assertThat(event).containsEntry("type", "error");
    assertThat(request(event))
        .containsEntry("path", "/logged")
        .containsEntry("user_agent", "IntegrationTest/1.0");
  }

  @Test
  void doesNotReportSuccessfulRequests() throws Exception {
    assertThat(get("/ok").statusCode()).isEqualTo(200);
    client.flush(Duration.ofSeconds(5));
    assertThat(events()).noneMatch(e -> "/ok".equals(String.valueOf(request(e).get("path"))));
  }

  private HttpResponse<String> get(String path, String... headers) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path));
    if (headers.length > 0) {
      builder.headers(headers);
    }
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private Map<String, Object> awaitEvent(Predicate<Map<String, Object>> match)
      throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    while (System.nanoTime() < deadline) {
      client.flush(Duration.ofSeconds(2));
      for (Map<String, Object> event : events()) {
        if (match.test(event)) {
          return event;
        }
      }
      Thread.sleep(50);
    }
    throw new AssertionError("No matching event; got " + events());
  }

  private static List<Map<String, Object>> events() {
    return SERVER.requests().stream()
        .filter(r -> r.path().equals("/api/v2/events"))
        .flatMap(r -> r.events().stream())
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> data(Map<String, Object> event) {
    Object data = event.get("data");
    return data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> error(Map<String, Object> event) {
    Object error = data(event).get("@error");
    return error instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> request(Map<String, Object> event) {
    Object request = data(event).get("@request");
    return request instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private static FakeServer startServer() {
    try {
      return new FakeServer();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
