package com.exceptionless.spring.boot;

import static com.exceptionless.TestClients.data;
import static com.exceptionless.TestClients.error;
import static com.exceptionless.TestClients.request;
import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.TestClients.CapturingSubmissionClient;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ExceptionlessWebFilterTest {
  private final CapturingSubmissionClient recorder = new CapturingSubmissionClient();
  private ExceptionlessClient client;

  @AfterEach
  void close() {
    if (client != null) {
      client.close();
    }
  }

  private ExceptionlessWebFilter filter(
      boolean captureNotFound, Consumer<ExceptionlessConfiguration> configure) {
    ExceptionlessConfiguration config = recorder.newConfiguration();
    configure.accept(config);
    client = new ExceptionlessClient(config).startup();
    return new ExceptionlessWebFilter(client, captureNotFound, 0);
  }

  private static MockServerWebExchange exchange(String uri) {
    return MockServerWebExchange.from(
        MockServerHttpRequest.method(HttpMethod.GET, URI.create(uri))
            .header("User-Agent", "TestAgent/1.0")
            .header("Authorization", "Bearer secret")
            .header("X-Custom", "one", "two")
            .cookie(new HttpCookie("session", "abc123")));
  }

  @Test
  void reportsErrorsWithRequestInfoAndPassesThemOn() {
    ExceptionlessWebFilter filter = filter(true, c -> {});
    IllegalStateException boom = new IllegalStateException("boom");

    StepVerifier.create(
            filter.filter(
                exchange("http://example.com:8080/orders?id=42&name=a%20b&flag&id=43"),
                failing(boom)))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(boom))
        .verify();

    Map<String, Object> event = recorder.awaitEvents(client, 1).get(0);
    assertThat(event).containsEntry("type", "error");
    assertThat(error(event)).containsEntry("message", "boom");
    assertThat(data(event)).containsEntry("@submission_method", "webFilter");
    Map<String, Object> request = request(event);
    assertThat(request)
        .containsEntry("http_method", "GET")
        .containsEntry("path", "/orders")
        .containsEntry("host", "example.com")
        .containsEntry("port", 8080L)
        .containsEntry("is_secure", false)
        .containsEntry("user_agent", "TestAgent/1.0")
        .containsEntry("query_string", Map.of("id", "42", "name", "a b", "flag", ""))
        .containsEntry("cookies", Map.of("session", "abc123"));
    @SuppressWarnings("unchecked")
    Map<String, Object> headers = (Map<String, Object>) request.get("headers");
    assertThat(headers).containsEntry("X-Custom", List.of("one", "two"));
    assertThat(headers.keySet()).noneMatch(name -> name.equalsIgnoreCase("authorization"));
  }

  @Test
  void reportsNotFoundErrorsAsNotFound() {
    ExceptionlessWebFilter filter = filter(true, c -> {});

    StepVerifier.create(
            filter.filter(
                exchange("/missing"), failing(new ResponseStatusException(HttpStatus.NOT_FOUND))))
        .verifyError(ResponseStatusException.class);

    Map<String, Object> event = recorder.awaitEvents(client, 1).get(0);
    assertThat(event).containsEntry("type", "404").containsEntry("source", "/missing");
    assertThat(request(event)).containsEntry("path", "/missing");
  }

  @Test
  void reportsNotFoundResponses() {
    ExceptionlessWebFilter filter = filter(true, c -> {});

    StepVerifier.create(filter.filter(exchange("/gone"), respondWith(HttpStatus.NOT_FOUND)))
        .verifyComplete();

    assertThat(recorder.awaitEvents(client, 1).get(0))
        .containsEntry("type", "404")
        .containsEntry("source", "/gone");
  }

  @Test
  void ignoresClientErrorsSuccessAndDisabledNotFound() {
    ExceptionlessWebFilter filter = filter(false, c -> {});

    StepVerifier.create(
            filter.filter(
                exchange("/bad"), failing(new ResponseStatusException(HttpStatus.BAD_REQUEST))))
        .verifyError(ResponseStatusException.class);
    StepVerifier.create(
            filter.filter(
                exchange("/missing"), failing(new ResponseStatusException(HttpStatus.NOT_FOUND))))
        .verifyError(ResponseStatusException.class);
    StepVerifier.create(filter.filter(exchange("/gone"), respondWith(HttpStatus.NOT_FOUND)))
        .verifyComplete();
    StepVerifier.create(filter.filter(exchange("/ok"), respondWith(HttpStatus.OK)))
        .verifyComplete();
    client.submitLog("marker");

    assertThat(recorder.awaitEvents(client, 1))
        .extracting(e -> e.get("message"))
        .containsExactly("marker");
  }

  @Test
  void reportsServerErrorStatusExceptions() {
    ExceptionlessWebFilter filter = filter(true, c -> {});

    StepVerifier.create(
            filter.filter(
                exchange("/x"),
                failing(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "down"))))
        .verifyError(ResponseStatusException.class);

    assertThat(recorder.awaitEvents(client, 1).get(0)).containsEntry("type", "error");
  }

  @Test
  void includesSignedInUserUnlessUserNamesAreExcluded() {
    ExceptionlessWebFilter filter = filter(true, c -> {});
    Principal alice = () -> "alice";
    ServerWebExchange exchange = exchange("/x").mutate().principal(Mono.just(alice)).build();

    StepVerifier.create(filter.filter(exchange, failing(new IllegalStateException("a"))))
        .verifyError(IllegalStateException.class);

    assertThat(data(recorder.awaitEvents(client, 1).get(0)).get("@user"))
        .isEqualTo(Map.of("identity", "alice"));
  }

  @Test
  void omitsSignedInUserWhenExcluded() {
    ExceptionlessWebFilter filter = filter(true, c -> c.includeUserName(false));
    Principal alice = () -> "alice";
    ServerWebExchange exchange = exchange("/x").mutate().principal(Mono.just(alice)).build();

    StepVerifier.create(filter.filter(exchange, failing(new IllegalStateException("a"))))
        .verifyError(IllegalStateException.class);

    assertThat(data(recorder.awaitEvents(client, 1).get(0))).doesNotContainKey("@user");
  }

  private static WebFilterChain failing(Throwable error) {
    return exchange -> Mono.error(error);
  }

  private static WebFilterChain respondWith(HttpStatus status) {
    return exchange -> Mono.fromRunnable(() -> exchange.getResponse().setStatusCode(status)).then();
  }
}
