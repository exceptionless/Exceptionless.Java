package com.exceptionless.servlet;

import com.exceptionless.models.RequestInfo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Captures an {@link HttpServletRequest} as a {@link RequestInfo} ({@code @request}).
 *
 * <p>The request is captured as is: the client's privacy settings ({@code includeHeaders}, {@code
 * includeCookies}, ...), the ignored headers ({@code Authorization}, {@code Cookie}, ...) and the
 * data exclusions are applied later by the event pipeline, so this is safe to call for any request.
 *
 * <pre>{@code
 * client.createException(e)
 *     .setRequestInfo(ServletRequestInfoFactory.create(request))
 *     .submit();
 * }</pre>
 *
 * <p>The request body is never read directly. Form fields ({@code post_data}) are only captured
 * when {@code includeFormData} is true, because reading the parameters of a form post makes the
 * container consume the body, which would break an application that reads it later.
 */
public final class ServletRequestInfoFactory {
  private ServletRequestInfoFactory() {}

  /** Captures the request, including form fields. Use once the request has been handled. */
  public static RequestInfo create(HttpServletRequest request) {
    return create(request, true);
  }

  /**
   * Captures the request.
   *
   * @param includeFormData whether to capture the fields of {@code
   *     application/x-www-form-urlencoded} and {@code multipart/form-data} posts. Only pass {@code
   *     true} once the application is done with the request body.
   */
  public static RequestInfo create(HttpServletRequest request, boolean includeFormData) {
    RequestInfo info =
        new RequestInfo()
            .setHttpMethod(request.getMethod())
            .setSecure(request.isSecure())
            .setHost(request.getServerName())
            .setPort(request.getServerPort())
            .setPath(request.getRequestURI())
            .setReferrer(request.getHeader("Referer"))
            .setUserAgent(request.getHeader("User-Agent"))
            .setClientIpAddress(request.getRemoteAddr());

    Map<String, List<String>> headers = headers(request);
    if (!headers.isEmpty()) {
      info.setHeaders(headers);
    }
    Map<String, String> cookies = cookies(request);
    if (!cookies.isEmpty()) {
      info.setCookies(cookies);
    }
    Map<String, String> query = parseQueryString(request.getQueryString());
    if (!query.isEmpty()) {
      info.setQueryString(query);
    }
    if (includeFormData) {
      Map<String, String> form = formData(request, query);
      if (!form.isEmpty()) {
        info.setPostData(form);
      }
    }
    return info;
  }

  private static Map<String, List<String>> headers(HttpServletRequest request) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    Enumeration<String> names = request.getHeaderNames();
    if (names == null) {
      return headers;
    }
    for (String name : Collections.list(names)) {
      Enumeration<String> values = request.getHeaders(name);
      List<String> list = values == null ? new ArrayList<>() : Collections.list(values);
      if (!list.isEmpty()) {
        headers.computeIfAbsent(name, k -> new ArrayList<>()).addAll(list);
      }
    }
    return headers;
  }

  private static Map<String, String> cookies(HttpServletRequest request) {
    Map<String, String> cookies = new LinkedHashMap<>();
    Cookie[] array;
    try {
      array = request.getCookies();
    } catch (RuntimeException e) {
      return cookies; // Malformed cookie header.
    }
    if (array != null) {
      for (Cookie cookie : array) {
        if (cookie != null && cookie.getName() != null && cookie.getValue() != null) {
          cookies.putIfAbsent(cookie.getName(), cookie.getValue());
        }
      }
    }
    return cookies;
  }

  /**
   * Parses a raw query string into its URL-decoded names and values. The first value wins when a
   * name repeats; names without a value map to an empty string.
   */
  static Map<String, String> parseQueryString(@Nullable String queryString) {
    Map<String, String> values = new LinkedHashMap<>();
    if (queryString == null || queryString.isEmpty()) {
      return values;
    }
    for (String pair : queryString.split("&")) {
      if (pair.isEmpty()) {
        continue;
      }
      int equals = pair.indexOf('=');
      String name = decode(equals < 0 ? pair : pair.substring(0, equals));
      String value = equals < 0 ? "" : decode(pair.substring(equals + 1));
      if (!name.isEmpty()) {
        values.putIfAbsent(name, value);
      }
    }
    return values;
  }

  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return value; // Invalid escape sequence: keep it raw.
    }
  }

  private static Map<String, String> formData(
      HttpServletRequest request, Map<String, String> query) {
    Map<String, String> form = new LinkedHashMap<>();
    String contentType = request.getContentType();
    if (contentType == null) {
      return form;
    }
    String type = contentType.toLowerCase(Locale.ROOT);
    if (!type.startsWith("application/x-www-form-urlencoded")
        && !type.startsWith("multipart/form-data")) {
      return form;
    }
    Map<String, String[]> parameters;
    try {
      parameters = request.getParameterMap();
    } catch (RuntimeException e) {
      return form; // E.g. multipart without a multipart config, or a body that is too large.
    }
    for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
      String name = entry.getKey();
      String[] values = entry.getValue();
      if (name == null || query.containsKey(name) || values == null || values.length == 0) {
        continue;
      }
      form.put(name, values.length == 1 ? values[0] : String.join(",", values));
    }
    return form;
  }
}
