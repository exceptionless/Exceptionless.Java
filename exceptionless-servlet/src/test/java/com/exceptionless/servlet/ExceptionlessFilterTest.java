package com.exceptionless.servlet;

import static com.exceptionless.TestClients.data;
import static com.exceptionless.TestClients.error;
import static com.exceptionless.TestClients.request;
import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.TestClients.CapturingSubmissionClient;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExceptionlessFilterTest {
  private final CapturingSubmissionClient recorder = new CapturingSubmissionClient();
  private ExceptionlessClient client;
  private JettyServer server;

  @AfterEach
  void tearDown() throws Exception {
    if (server != null) {
      server.close();
    }
    if (client != null) {
      client.close();
    }
  }

  private void start(Consumer<ExceptionlessConfiguration> configure, Consumer<JettyServer> filters)
      throws Exception {
    ExceptionlessConfiguration config = recorder.newConfiguration();
    configure.accept(config);
    client = new ExceptionlessClient(config).startup();
    server = new JettyServer();
    filters.accept(server);
    server.servlet(new TestServlet(client)).start();
  }

  private void start() throws Exception {
    start(c -> {}, s -> s.filter(new ExceptionlessFilter(client)));
  }

  @Test
  void reportsUnhandledExceptionWithRequestInfo() throws Exception {
    start();

    HttpResponse<String> response =
        server.get(
            "/fail?id=42&name=John%20Doe&id=43&flag",
            "Authorization",
            "Bearer secret",
            "Cookie",
            "session=abc123; theme=dark",
            "User-Agent",
            "TestAgent/1.0",
            "Referer",
            "https://example.com/start",
            "X-Custom",
            "one",
            "X-Custom",
            "two");
    assertThat(response.statusCode()).isEqualTo(500);

    List<Map<String, Object>> events = recorder.awaitEvents(client, 1);
    assertThat(events).hasSize(1);
    Map<String, Object> event = events.get(0);
    assertThat(event.get("type")).isEqualTo("error");
    assertThat(error(event))
        .containsEntry("type", "java.lang.IllegalStateException")
        .containsEntry("message", "boom");
    assertThat(data(event)).containsEntry("@submission_method", "servletFilter");

    Map<String, Object> request = request(event);
    assertThat(request)
        .containsEntry("http_method", "GET")
        .containsEntry("path", "/fail")
        .containsEntry("host", "127.0.0.1")
        .containsEntry("is_secure", false)
        .containsEntry("user_agent", "TestAgent/1.0")
        .containsEntry("referrer", "https://example.com/start")
        .containsEntry("client_ip_address", "127.0.0.1")
        .containsKey("port");
    assertThat(request.get("query_string"))
        .isEqualTo(Map.of("id", "42", "name", "John Doe", "flag", ""));
    assertThat(request.get("cookies")).isEqualTo(Map.of("session", "abc123", "theme", "dark"));
    @SuppressWarnings("unchecked")
    Map<String, Object> headers = (Map<String, Object>) request.get("headers");
    assertThat(headers.keySet())
        .noneMatch(name -> name.equalsIgnoreCase("authorization"))
        .noneMatch(name -> name.equalsIgnoreCase("cookie"))
        .noneMatch(name -> name.equalsIgnoreCase("user-agent"))
        .noneMatch(name -> name.equalsIgnoreCase("host"));
    assertThat(headers.entrySet())
        .anySatisfy(
            header -> {
              assertThat(header.getKey()).isEqualToIgnoringCase("X-Custom");
              assertThat(header.getValue()).isEqualTo(List.of("one", "two"));
            });
    assertThat(request).doesNotContainKey("post_data");
  }

  @Test
  void unwrapsServletExceptionRootCause() throws Exception {
    start();

    assertThat(server.get("/wrapped").statusCode()).isEqualTo(500);

    Map<String, Object> event = recorder.awaitEvents(client, 1).get(0);
    assertThat(error(event))
        .containsEntry("type", "java.lang.IllegalArgumentException")
        .containsEntry("message", "bad input");
  }

  @Test
  void rethrowsTheOriginalException() throws Exception {
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    Filter outer =
        (request, response, chain) -> {
          try {
            chain.doFilter(request, response);
          } catch (IOException | ServletException | RuntimeException e) {
            thrown.set(e);
            throw e;
          }
        };
    start(c -> {}, s -> s.filter(outer).filter(new ExceptionlessFilter(client)));

    server.get("/wrapped");

    assertThat(thrown.get())
        .isExactlyInstanceOf(ServletException.class)
        .hasMessage("Request processing failed");
    assertThat(recorder.awaitEvents(client, 1)).hasSize(1);
  }

  @Test
  void reportsNotFound() throws Exception {
    start();

    assertThat(server.get("/missing?page=2", "User-Agent", "Mozilla/5.0").statusCode())
        .isEqualTo(404);

    Map<String, Object> event = recorder.awaitEvents(client, 1).get(0);
    assertThat(event.get("type")).isEqualTo("404");
    assertThat(event.get("source")).isEqualTo("/missing");
    assertThat(data(event)).containsEntry("@submission_method", "servletFilter");
    assertThat(request(event))
        .containsEntry("path", "/missing")
        .containsEntry("query_string", Map.of("page", "2"));
  }

  @Test
  void doesNotReportNotFoundWhenDisabled() throws Exception {
    start(
        c -> {},
        s -> s.filter(new ExceptionlessFilter(client), Map.of("captureNotFound", "false")));

    assertThat(server.get("/missing").statusCode()).isEqualTo(404);
    client.submitLog("marker");

    List<Map<String, Object>> events = recorder.awaitEvents(client, 1);
    assertThat(events).extracting(e -> e.get("message")).containsExactly("marker");
  }

  @Test
  void doesNotReportSuccessfulRequests() throws Exception {
    start();

    assertThat(server.get("/ok").statusCode()).isEqualTo(200);
    client.submitLog("marker");

    assertThat(recorder.awaitEvents(client, 1))
        .extracting(e -> e.get("message"))
        .containsExactly("marker");
  }

  @Test
  void stripsPrivateInformationWhenDisabled() throws Exception {
    start(c -> c.includePrivateInformation(false), s -> s.filter(new ExceptionlessFilter(client)));

    server.get("/fail?token=abc", "Cookie", "session=abc123", "X-Custom", "value");

    Map<String, Object> request = request(recorder.awaitEvents(client, 1).get(0));
    assertThat(request).containsEntry("path", "/fail").containsEntry("http_method", "GET");
    assertThat(request)
        .doesNotContainKeys("cookies", "query_string", "headers", "client_ip_address", "post_data");
  }

  @Test
  void appliesDataExclusionsToRequest() throws Exception {
    start(
        c -> c.addDataExclusions("*token*", "session"),
        s -> s.filter(new ExceptionlessFilter(client)));

    server.get(
        "/fail?access_token=abc&page=1",
        "Cookie",
        "session=abc123; theme=dark",
        "X-Token",
        "t",
        "X-Other",
        "o");

    Map<String, Object> request = request(recorder.awaitEvents(client, 1).get(0));
    assertThat(request.get("query_string")).isEqualTo(Map.of("page", "1"));
    assertThat(request.get("cookies")).isEqualTo(Map.of("theme", "dark"));
    @SuppressWarnings("unchecked")
    Map<String, Object> headers = (Map<String, Object>) request.get("headers");
    assertThat(headers.keySet())
        .noneMatch(name -> name.equalsIgnoreCase("x-token"))
        .anyMatch(name -> name.equalsIgnoreCase("x-other"));
  }

  @Test
  void capturesFormFieldsButNotQueryStringInPostData() throws Exception {
    start();

    server.postForm("/fail?page=1", "name=Jane&color=red&color=blue");

    Map<String, Object> request = request(recorder.awaitEvents(client, 1).get(0));
    assertThat(request).containsEntry("http_method", "POST");
    assertThat(request.get("post_data")).isEqualTo(Map.of("name", "Jane", "color", "red,blue"));
    assertThat(request.get("query_string")).isEqualTo(Map.of("page", "1"));
  }

  @Test
  void addsRequestInfoToErrorsSubmittedDuringTheRequest() throws Exception {
    start();

    assertThat(server.get("/logged?q=1", "User-Agent", "TestAgent/1.0").statusCode())
        .isEqualTo(200);

    List<Map<String, Object>> events = recorder.awaitEvents(client, 2);
    Map<String, Object> error =
        events.stream().filter(e -> "error".equals(e.get("type"))).findFirst().orElseThrow();
    assertThat(request(error))
        .containsEntry("path", "/logged")
        .containsEntry("user_agent", "TestAgent/1.0")
        .containsEntry("query_string", Map.of("q", "1"));
    assertThat(data(error)).doesNotContainKey("@submission_method");
    Map<String, Object> log =
        events.stream().filter(e -> "log".equals(e.get("type"))).findFirst().orElseThrow();
    assertThat(data(log)).doesNotContainKey("@request");
  }

  @Test
  void doesNotAddRequestInfoOutsideOfRequests() throws Exception {
    start();
    server.get("/ok");

    client.submitException(new IllegalStateException("background"));

    Map<String, Object> event = recorder.awaitEvents(client, 1).get(0);
    assertThat(data(event)).doesNotContainKey("@request");
    assertThat(ServletRequestInfoPlugin.getCurrentRequest()).isNull();
  }

  @Test
  void reportsExceptionOnceWhenLoggedAndThrown() throws Exception {
    start();

    assertThat(server.get("/logged-and-thrown").statusCode()).isEqualTo(500);
    client.submitLog("marker");

    List<Map<String, Object>> events = recorder.awaitEvents(client, 2);
    assertThat(events).extracting(e -> e.get("type")).containsExactlyInAnyOrder("error", "log");
    Map<String, Object> error =
        events.stream().filter(e -> "error".equals(e.get("type"))).findFirst().orElseThrow();
    assertThat(error(error)).containsEntry("message", "twice");
    assertThat(request(error)).containsEntry("path", "/logged-and-thrown");
  }

  @Test
  void includesSignedInUser() throws Exception {
    start(c -> {}, s -> s.filter(principalFilter("alice")).filter(new ExceptionlessFilter(client)));

    server.get("/fail");

    Map<String, Object> event = recorder.awaitEvents(client, 1).get(0);
    assertThat(data(event).get("@user")).isEqualTo(Map.of("identity", "alice"));
  }

  @Test
  void omitsSignedInUserWhenUserNamesAreExcluded() throws Exception {
    start(
        c -> c.includeUserName(false),
        s -> s.filter(principalFilter("alice")).filter(new ExceptionlessFilter(client)));

    server.get("/fail");

    Map<String, Object> event = recorder.awaitEvents(client, 1).get(0);
    assertThat(data(event)).doesNotContainKey("@user");
  }

  @Test
  void reportsAsyncNotFoundWhenTheRequestCompletes() throws Exception {
    start();

    assertThat(server.get("/async-missing").statusCode()).isEqualTo(404);

    Map<String, Object> event = recorder.awaitEvents(client, 1).get(0);
    assertThat(event.get("type")).isEqualTo("404");
    assertThat(event.get("source")).isEqualTo("/async-missing");
  }

  @Test
  void doesNotReportSuccessfulAsyncRequests() throws Exception {
    start();

    assertThat(server.get("/async-ok").statusCode()).isEqualTo(200);
    client.submitLog("marker");

    assertThat(recorder.awaitEvents(client, 1))
        .extracting(e -> e.get("message"))
        .containsExactly("marker");
  }

  @Test
  void reportsAsyncDispatchErrorsOnce() throws Exception {
    start(
        c -> {},
        s ->
            s.filter(
                new ExceptionlessFilter(client),
                Map.of(),
                DispatcherType.REQUEST,
                DispatcherType.ASYNC));

    assertThat(server.get("/async-fail").statusCode()).isEqualTo(500);
    client.submitLog("marker");

    List<Map<String, Object>> events = recorder.awaitEvents(client, 2);
    assertThat(events).extracting(e -> e.get("type")).containsExactlyInAnyOrder("error", "log");
    Map<String, Object> error =
        events.stream().filter(e -> "error".equals(e.get("type"))).findFirst().orElseThrow();
    assertThat(error(error)).containsEntry("message", "boom");
    assertThat(request(error)).containsKey("path");
  }

  @Test
  void usesTheDefaultClientWhenNoneIsGiven() throws Exception {
    ExceptionlessClient previous = Exceptionless.hasDefault() ? Exceptionless.getDefault() : null;
    try {
      start(
          c -> {},
          s -> {
            Exceptionless.setDefault(client);
            s.filter(new ExceptionlessFilter());
          });

      server.get("/fail");

      assertThat(recorder.awaitEvents(client, 1).get(0).get("type")).isEqualTo("error");
    } finally {
      Exceptionless.setDefault(previous);
    }
  }

  @Test
  void passesRequestsThroughUntilTheDefaultClientHasStarted() throws Exception {
    ExceptionlessClient previous = Exceptionless.hasDefault() ? Exceptionless.getDefault() : null;
    Exceptionless.setDefault(null);
    ExceptionlessClient notStarted = new ExceptionlessClient(recorder.newConfiguration());
    try {
      start(c -> {}, s -> s.filter(new ExceptionlessFilter()));

      assertThat(server.get("/fail").statusCode()).isEqualTo(500);
      assertThat(Exceptionless.hasDefault()).as("the filter never creates it").isFalse();

      Exceptionless.setDefault(notStarted);
      assertThat(server.get("/fail").statusCode()).isEqualTo(500);
      assertThat(notStarted.isStarted()).as("the filter never starts it").isFalse();

      client.submitLog("marker");
      assertThat(recorder.awaitEvents(client, 1))
          .extracting(e -> e.get("message"))
          .containsExactly("marker");
    } finally {
      notStarted.close();
      Exceptionless.setDefault(previous);
    }
  }

  @Test
  void followsTheDefaultClientWhenItIsReplaced() throws Exception {
    ExceptionlessClient previous = Exceptionless.hasDefault() ? Exceptionless.getDefault() : null;
    CapturingSubmissionClient otherRecorder = new CapturingSubmissionClient();
    ExceptionlessClient other = otherRecorder.newClient();
    try {
      start(
          c -> {},
          s -> {
            Exceptionless.setDefault(client);
            s.filter(new ExceptionlessFilter());
          });
      server.get("/fail");
      assertThat(recorder.awaitEvents(client, 1)).hasSize(1);

      Exceptionless.setDefault(other);
      server.get("/missing");

      Map<String, Object> event = otherRecorder.awaitEvents(other, 1).get(0);
      assertThat(event.get("type")).isEqualTo("404");
      assertThat(request(event)).containsEntry("path", "/missing");
      assertThat(other.getConfiguration().hasPlugin(ServletRequestInfoPlugin.NAME)).isTrue();
      assertThat(recorder.flush(client)).hasSize(1);
    } finally {
      other.close();
      Exceptionless.setDefault(previous);
    }
  }

  @Test
  void registersRequestPluginOnce() throws Exception {
    start();
    server.get("/ok");
    server.get("/ok");

    assertThat(client.getConfiguration().getPlugins())
        .filteredOn(p -> p.getName().equals(ServletRequestInfoPlugin.NAME))
        .hasSize(1);
  }

  private static Filter principalFilter(String name) {
    return (request, response, chain) ->
        chain.doFilter(
            new HttpServletRequestWrapper((HttpServletRequest) request) {
              @Override
              public Principal getUserPrincipal() {
                return () -> name;
              }
            },
            response);
  }

  /** Routes requests by path. */
  static final class TestServlet extends HttpServlet {
    private final transient ExceptionlessClient client;

    TestServlet(ExceptionlessClient client) {
      this.client = client;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
      String path = request.getRequestURI();
      switch (path) {
        case "/fail" -> throw new IllegalStateException("boom");
        case "/wrapped" ->
            throw new ServletException(
                "Request processing failed", new IllegalArgumentException("bad input"));
        case "/missing" -> response.sendError(404);
        case "/logged" -> {
          client.createException(new IllegalStateException("logged")).submit();
          client.submitLog("Something happened");
          response.getWriter().write("ok");
        }
        case "/logged-and-thrown" -> {
          IllegalStateException error = new IllegalStateException("twice");
          client.submitException(error);
          throw error;
        }
        case "/async-missing" -> {
          AsyncContext async = request.startAsync();
          async.start(
              () -> {
                ((HttpServletResponse) async.getResponse()).setStatus(404);
                async.complete();
              });
        }
        case "/async-ok" -> {
          AsyncContext async = request.startAsync();
          async.start(
              () -> {
                try {
                  async.getResponse().getWriter().write("ok");
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
                async.complete();
              });
        }
        case "/async-fail" -> {
          if (request.getDispatcherType() == DispatcherType.ASYNC) {
            throw new IllegalStateException("boom");
          }
          AsyncContext async = request.startAsync();
          async.start(async::dispatch);
        }
        default -> response.getWriter().write("ok");
      }
    }
  }
}
