package com.exceptionless.jul;

import com.exceptionless.json.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

/** An in-process HTTP server that behaves like the Exceptionless API, for tests. */
final class FakeServer implements AutoCloseable {
  /** A request received by the server. */
  public record Request(
      String method, String path, String query, Map<String, List<String>> headers, String body) {
    public String header(String name) {
      for (Map.Entry<String, List<String>> e : headers.entrySet()) {
        if (e.getKey().equalsIgnoreCase(name)) {
          return e.getValue().get(0);
        }
      }
      return null;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> events() {
      Object parsed = JsonParser.parse(body);
      return (List<Map<String, Object>>) parsed;
    }
  }

  /** A canned response. */
  public record Response(int status, String body, Map<String, String> headers) {
    public static Response of(int status) {
      return new Response(status, "", Map.of());
    }
  }

  private final HttpServer server;
  private final LinkedBlockingQueue<Request> requests = new LinkedBlockingQueue<>();
  private final List<Request> allRequests = new CopyOnWriteArrayList<>();
  private volatile Function<Request, Response> eventsHandler = r -> Response.of(202);
  private volatile Function<Request, Response> configHandler =
      r -> new Response(200, "{\"version\":1,\"settings\":{}}", Map.of());
  private final AtomicInteger configVersion = new AtomicInteger(0);

  public FakeServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  public String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public void onEvents(Function<Request, Response> handler) {
    this.eventsHandler = handler;
  }

  public void onConfig(Function<Request, Response> handler) {
    this.configHandler = handler;
  }

  /** The value returned in X-Exceptionless-ConfigVersion on event posts (0 = not sent). */
  public void setConfigVersionHeader(int version) {
    configVersion.set(version);
  }

  public Request take(Duration timeout) throws InterruptedException {
    Request request = requests.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (request == null) {
      throw new AssertionError("No request received within " + timeout);
    }
    return request;
  }

  /** Waits for the next request to the given path prefix, skipping others. */
  public Request take(String pathPrefix, Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      long remaining = deadline - System.nanoTime();
      Request request = requests.poll(Math.max(0, remaining), TimeUnit.NANOSECONDS);
      if (request == null) {
        throw new AssertionError(
            "No request to " + pathPrefix + " within " + timeout + "; got " + paths());
      }
      if (request.path().startsWith(pathPrefix)) {
        return request;
      }
    }
  }

  public List<Request> requests() {
    return new ArrayList<>(allRequests);
  }

  public List<String> paths() {
    return allRequests.stream().map(Request::path).toList();
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      byte[] raw = readAll(exchange.getRequestBody());
      if ("gzip".equals(exchange.getRequestHeaders().getFirst("Content-Encoding"))) {
        raw = readAll(new GZIPInputStream(new java.io.ByteArrayInputStream(raw)));
      }
      String query = exchange.getRequestURI().getRawQuery();
      Request request =
          new Request(
              exchange.getRequestMethod(),
              exchange.getRequestURI().getPath(),
              query == null ? "" : query,
              Map.copyOf(exchange.getRequestHeaders()),
              new String(raw, StandardCharsets.UTF_8));
      allRequests.add(request);
      requests.add(request);

      Response response;
      String path = request.path();
      if (path.equals("/api/v2/events")) {
        response = eventsHandler.apply(request);
        if (configVersion.get() > 0) {
          exchange
              .getResponseHeaders()
              .add("X-Exceptionless-ConfigVersion", Integer.toString(configVersion.get()));
        }
      } else if (path.equals("/api/v2/projects/config")) {
        response = configHandler.apply(request);
      } else {
        response = Response.of(path.contains("heartbeat") ? 200 : 202);
      }
      response.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
      byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
      if (response.status() == 304 || body.length == 0) {
        exchange.sendResponseHeaders(response.status(), -1);
      } else {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(body);
        }
      }
    }
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    in.transferTo(out);
    return out.toByteArray();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
