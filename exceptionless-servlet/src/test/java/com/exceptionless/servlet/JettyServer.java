package com.exceptionless.servlet;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.EnumSet;
import java.util.Map;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

/** An embedded Jetty server running a servlet behind filters, for tests. */
final class JettyServer implements AutoCloseable {
  private final Server server;
  private final ServletContextHandler context;
  private final HttpClient http =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

  JettyServer() {
    server = new Server(0);
    context = new ServletContextHandler();
    context.setContextPath("/");
    server.setHandler(context);
  }

  JettyServer filter(Filter filter, Map<String, String> initParameters, DispatcherType... types) {
    FilterHolder holder = new FilterHolder(filter);
    holder.setAsyncSupported(true);
    holder.setInitParameters(initParameters);
    EnumSet<DispatcherType> dispatcherTypes =
        types.length == 0 ? EnumSet.of(DispatcherType.REQUEST) : EnumSet.of(types[0], types);
    context.addFilter(holder, "/*", dispatcherTypes);
    return this;
  }

  JettyServer filter(Filter filter) {
    return filter(filter, Map.of());
  }

  JettyServer servlet(HttpServlet servlet) {
    ServletHolder holder = new ServletHolder(servlet);
    holder.setAsyncSupported(true);
    context.addServlet(holder, "/*");
    return this;
  }

  JettyServer start() throws Exception {
    server.start();
    return this;
  }

  String url(String path) {
    return "http://127.0.0.1:"
        + ((ServerConnector) server.getConnectors()[0]).getLocalPort()
        + path;
  }

  HttpResponse<String> get(String path, String... headers) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path))).GET();
    if (headers.length > 0) {
      builder.headers(headers);
    }
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> postForm(String path, String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url(path)))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Override
  public void close() {
    try {
      server.stop();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
