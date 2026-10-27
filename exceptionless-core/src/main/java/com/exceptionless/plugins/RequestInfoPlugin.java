package com.exceptionless.plugins;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.internal.Strings;
import com.exceptionless.models.Event;
import com.exceptionless.models.RequestInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Attaches the request from the context data and applies the privacy settings and data exclusions
 * to it, whoever created it. Integrations only need to capture the raw request.
 */
public final class RequestInfoPlugin implements EventPlugin {
  public static final int PRIORITY = 60;

  /** Headers that are never sent: credentials, and values already captured elsewhere. */
  private static final Set<String> IGNORED_HEADERS =
      Set.of(
          "authorization",
          "proxy-authorization",
          "cookie",
          "set-cookie",
          "x-api-key",
          "x-auth-token",
          "x-csrf-token",
          "x-xsrf-token",
          "host",
          "referer",
          "user-agent");

  static final int MAX_VALUE_LENGTH = 1000;

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void run(EventPluginContext context) {
    Event event = context.getEvent();
    RequestInfo request = event.getRequestInfo();
    if (request == null) {
      request = context.getContextData().getRequestInfo();
      if (request == null) {
        return;
      }
      event.setRequestInfo(request);
    }
    sanitize(request, context.getConfiguration());
  }

  /** Applies the privacy settings and data exclusions to {@code request} in place. */
  private static void sanitize(RequestInfo request, ExceptionlessConfiguration config) {
    Predicate<String> excluded = config::isDataExcluded;

    if (!config.isIncludeIpAddress()) {
      request.setClientIpAddress(null);
    }
    if (!config.isIncludeCookies()) {
      request.setCookies(null);
    } else {
      request.setCookies(filter(request.getCookies(), excluded));
    }
    if (!config.isIncludeQueryString()) {
      request.setQueryString(null);
    } else {
      request.setQueryString(filter(request.getQueryString(), excluded));
    }
    if (!config.isIncludePostData()) {
      request.setPostData(null);
    } else if (request.getPostData() instanceof Map<?, ?> form) {
      Map<String, Object> filtered = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : form.entrySet()) {
        String key = String.valueOf(entry.getKey());
        if (!excluded.test(key) && entry.getValue() != null) {
          filtered.put(key, truncateFormValue(entry.getValue()));
        }
      }
      request.setPostData(filtered.isEmpty() ? null : filtered);
    }
    Map<String, List<String>> headers = request.getHeaders();
    if (!config.isIncludeHeaders()) {
      request.setHeaders(null);
    } else if (headers != null) {
      Map<String, List<String>> filtered = new LinkedHashMap<>();
      for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
        String name = entry.getKey();
        if (name == null
            || IGNORED_HEADERS.contains(name.toLowerCase(Locale.ROOT))
            || excluded.test(name)
            || entry.getValue() == null) {
          continue;
        }
        List<String> values =
            entry.getValue().stream()
                .filter(Objects::nonNull)
                .map(RequestInfoPlugin::truncate)
                .toList();
        filtered.put(name, values);
      }
      request.setHeaders(filtered.isEmpty() ? null : filtered);
    }
  }

  private static @Nullable Map<String, String> filter(
      @Nullable Map<String, String> values, Predicate<String> excluded) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    Map<String, String> filtered = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null && !excluded.test(entry.getKey())) {
        filtered.put(entry.getKey(), truncate(entry.getValue()));
      }
    }
    return filtered.isEmpty() ? null : filtered;
  }

  private static String truncate(String value) {
    return Strings.truncate(value, MAX_VALUE_LENGTH);
  }

  /** Form values are usually strings, but integrations may pass other simple values as-is. */
  private static Object truncateFormValue(Object value) {
    return value instanceof String text ? truncate(text) : value;
  }
}
