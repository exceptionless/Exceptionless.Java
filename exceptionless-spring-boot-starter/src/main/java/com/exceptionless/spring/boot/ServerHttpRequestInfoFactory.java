package com.exceptionless.spring.boot;

import com.exceptionless.models.RequestInfo;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * Captures a WebFlux {@link ServerHttpRequest} as a {@link RequestInfo} ({@code @request}). The
 * request body is never read. Privacy settings and data exclusions are applied later by the event
 * pipeline.
 *
 * <pre>{@code
 * client.createException(e)
 *     .setRequestInfo(ServerHttpRequestInfoFactory.create(exchange.getRequest()))
 *     .submit();
 * }</pre>
 */
public final class ServerHttpRequestInfoFactory {
  private ServerHttpRequestInfoFactory() {}

  public static RequestInfo create(ServerHttpRequest request) {
    URI uri = request.getURI();
    HttpHeaders headers = request.getHeaders();
    boolean secure = request.getSslInfo() != null || "https".equalsIgnoreCase(uri.getScheme());
    int port = uri.getPort();
    if (port < 0) {
      port = secure ? 443 : 80;
    }
    RequestInfo info = new RequestInfo();
    info.setHttpMethod(request.getMethod().name());
    info.setSecure(secure);
    info.setHost(uri.getHost());
    info.setPort(port);
    info.setPath(uri.getRawPath());
    info.setReferrer(headers.getFirst("Referer"));
    info.setUserAgent(headers.getFirst("User-Agent"));

    InetSocketAddress remote = request.getRemoteAddress();
    if (remote != null) {
      info.setClientIpAddress(
          remote.getAddress() != null
              ? remote.getAddress().getHostAddress()
              : remote.getHostString());
    }

    Map<String, List<String>> headerMap = new LinkedHashMap<>();
    headers.forEach(
        (name, values) -> {
          if (values != null && !values.isEmpty()) {
            headerMap.put(name, List.copyOf(values));
          }
        });
    if (!headerMap.isEmpty()) {
      info.setHeaders(headerMap);
    }

    Map<String, String> cookies = new LinkedHashMap<>();
    request
        .getCookies()
        .forEach(
            (name, values) -> {
              if (values != null && !values.isEmpty()) {
                HttpCookie cookie = values.get(0);
                if (cookie.getValue() != null) {
                  cookies.put(name, cookie.getValue());
                }
              }
            });
    if (!cookies.isEmpty()) {
      info.setCookies(cookies);
    }

    Map<String, String> query = parseQueryString(uri.getRawQuery());
    if (!query.isEmpty()) {
      info.setQueryString(query);
    }
    return info;
  }

  /** URL-decoded query string names and values; the first value wins when a name repeats. */
  static Map<String, String> parseQueryString(@Nullable String rawQuery) {
    Map<String, String> values = new LinkedHashMap<>();
    if (rawQuery == null || rawQuery.isEmpty()) {
      return values;
    }
    for (String pair : rawQuery.split("&")) {
      int equals = pair.indexOf('=');
      String name = decode(equals < 0 ? pair : pair.substring(0, equals));
      if (!name.isEmpty()) {
        values.putIfAbsent(name, equals < 0 ? "" : decode(pair.substring(equals + 1)));
      }
    }
    return values;
  }

  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return value;
    }
  }
}
