package com.exceptionless.exceptionlessclient.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.*;

public final class Utils {
  public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private Utils() {}

  public static <T> T safeGetAs(Object value, Class<T> cls) {
    if (value == null) {
      return null;
    }

    try {
      return cls.cast(value);
    } catch (ClassCastException ignored) {
      return null;
    }
  }

  public static Map<String, String> getCookies(HttpRequest request) {
    Optional<String> maybeRawCookie = request.headers().firstValue("Cookie");
    if (!maybeRawCookie.isPresent()) {
      return new HashMap<>();
    }

    Map<String, String> cookies = new HashMap<>();
    String[] rawCookieParams = maybeRawCookie.get().split(";");
    for (String rawCookieNameAndValue : rawCookieParams) {
      String[] rawCookieNameAndValuePair = rawCookieNameAndValue.split("=");
      cookies.put(rawCookieNameAndValuePair[0], rawCookieNameAndValuePair[1]);
    }

    return cookies;
  }

  public static Map<String, List<String>> getQueryParams(URI uri) {
    Map<String, List<String>> queryParams = new HashMap<>();

    String[] rawQueryParams = uri.getQuery().split("&");
    for (String queryParamsNameAndValue : rawQueryParams) {
      String[] queryParamsNameAndValuePair = queryParamsNameAndValue.split("=");
      if (!queryParams.containsKey(queryParamsNameAndValuePair[0])) {
        queryParams.put(queryParamsNameAndValuePair[0], new ArrayList<>());
      }

      queryParams.get(queryParamsNameAndValuePair[0]).add(queryParamsNameAndValuePair[1]);
    }

    return queryParams;
  }

  public static boolean match(String value, String pattern) {
    // todo check this works or not;
    return pattern.matches(value);
  }
}
