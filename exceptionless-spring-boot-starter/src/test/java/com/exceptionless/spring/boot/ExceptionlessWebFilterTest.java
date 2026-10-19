package com.exceptionless.spring.boot;

import static com.exceptionless.spring.boot.RecordingSubmissionClient.data;
import static com.exceptionless.spring.boot.RecordingSubmissionClient.error;
import static com.exceptionless.spring.boot.RecordingSubmissionClient.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
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

class ExceptionlessWebFilterTest {
  private final RecordingSubmissionClient recorder = new RecordingSubmissionClient();
  private ExceptionlessClient client;

  @AfterEach
  void close() {
    if (client != null) {
      client.close();
    }
  }

  private ExceptionlessWebFilter filter(
      boolean captureNotFound, Consumer<ExceptionlessConfiguration> configure) {
    ExceptionlessConfiguration config = RecordingSubmissionClient.configuration(recorder);
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
  void reportsErrorsWithRequestInfoAndPassesThemOn() throws Exception {
    ExceptionlessWebFilter filter = filter(true, c -> {});
    IllegalStateException boom = new IllegalStateException("boom");

    assertThatThrownBy(
            () ->
                filter
                    .filter(
                        exchange("http://example.com:8080/orders?id=42&name=a%20b"), failing(boom))
                    .block())
        .isSameAs(boom);

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
        .containsEntry("query_string", Map.of("id", "42", "name", "a b"))
        .containsEntry("cookies", Map.of("session", "abc123"));
    @SuppressWarnings("unchecked")
    Map<String, Object> headers = (Map<String, Object>) request.get("headers");
    assertThat(headers).containsEntry("X-Custom", List.of("one", "two"));
    assertThat(headers.keySet()).noneMatch(name -> name.equalsIgnoreCase("authorization"));
  }

  @Test
  void reportsNotFoundErrorsAsNotFound() throws Exception {
    ExceptionlessWebFilter filter = filter(true, c -> {});

    assertThatThrownBy(
            () ->
                filter
                    .filter(
                        exchange("/missing"),
                        failing(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                    .block())
        .isInstanceOf(ResponseStatusException.class);

    Map<String, Object> event = recorder.awaitEvents(client, 1).get(0);
    assertThat(event).containsEntry("type", "404").containsEntry("source", "/missing");
    assertThat(request(event)).containsEntry("path", "/missing");
  }

  @Test
  void reportsNotFoundResponses() throws Exception {
    ExceptionlessWebFilter filter = filter(true, c -> {});

    filter.filter(exchange("/gone"), respondWith(HttpStatus.NOT_FOUND)).block();

    assertThat(recorder.awaitEvents(client, 1).get(0))
        .containsEntry("type", "404")
        .containsEntry("source", "/gone");
  }

  @Test
  void ignoresClientErrorsSuccessAndDisabledNotFound() throws Exception {
    ExceptionlessWebFilter filter = filter(false, c -> {});

    assertThatThrownBy(
            () ->
                filter
                    .filter(
                        exchange("/bad"),
                        failing(new ResponseStatusException(HttpStatus.BAD_REQUEST)))
                    .block())
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                filter
                    .filter(
                        exchange("/missing"),
                        failing(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                    .block())
        .isInstanceOf(ResponseStatusException.class);
    filter.filter(exchange("/gone"), respondWith(HttpStatus.NOT_FOUND)).block();
    filter.filter(exchange("/ok"), respondWith(HttpStatus.OK)).block();
    client.submitLog("marker");

    assertThat(recorder.awaitEvents(client, 1))
        .extracting(e -> e.get("message"))
        .containsExactly("marker");
  }

  @Test
  void reportsServerErrorStatusExceptions() throws Exception {
    ExceptionlessWebFilter filter = filter(true, c -> {});

    assertThatThrownBy(
            () ->
                filter
                    .filter(
                        exchange("/x"),
                        failing(
                            new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "down")))
                    .block())
        .isInstanceOf(ResponseStatusException.class);

    assertThat(recorder.awaitEvents(client, 1).get(0)).containsEntry("type", "error");
  }

  @Test
  void includesSignedInUserUnlessUserNamesAreExcluded() throws Exception {
    ExceptionlessWebFilter filter = filter(true, c -> {});
    Principal alice = () -> "alice";
    ServerWebExchange exchange = exchange("/x").mutate().principal(Mono.just(alice)).build();

    assertThatThrownBy(
            () -> filter.filter(exchange, failing(new IllegalStateException("a"))).block())
        .isInstanceOf(IllegalStateException.class);

    assertThat(data(recorder.awaitEvents(client, 1).get(0)).get("@user"))
        .isEqualTo(Map.of("identity", "alice"));
  }

  @Test
  void omitsSignedInUserWhenExcluded() throws Exception {
    ExceptionlessWebFilter filter = filter(true, c -> c.includeUserName(false));
    Principal alice = () -> "alice";
    ServerWebExchange exchange = exchange("/x").mutate().principal(Mono.just(alice)).build();

    assertThatThrownBy(
            () -> filter.filter(exchange, failing(new IllegalStateException("a"))).block())
        .isInstanceOf(IllegalStateException.class);

    assertThat(data(recorder.awaitEvents(client, 1).get(0))).doesNotContainKey("@user");
  }

  private static WebFilterChain failing(Throwable error) {
    return exchange -> Mono.error(error);
  }

  private static WebFilterChain respondWith(HttpStatus status) {
    return exchange -> Mono.fromRunnable(() -> exchange.getResponse().setStatusCode(status)).then();
  }
}
