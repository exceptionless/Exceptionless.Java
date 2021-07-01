package com.exceptionless.exceptionlessclient.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.*;

public final class Utils {
  public static final ObjectMapper JSON_MAPPER;

  static {
    JSON_MAPPER = new ObjectMapper();
    JSON_MAPPER.registerModule(new JavaTimeModule());
    JSON_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    JSON_MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
  }

  private static final Logger LOG = LoggerFactory.getLogger(Utils.class);

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
    if (maybeRawCookie.isEmpty()) {
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
    String query = uri.getQuery();
    if (query == null || query.isBlank()) {
      return Map.of();
    }

    String[] rawQueryParams = query.split("&");
    Map<String, List<String>> queryParams = new HashMap<>();
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
    if (value == null) {
      return false;
    }
    if (pattern == null) {
      return false;
    }
    // todo check this works or not;
    boolean result = value.toLowerCase().matches(pattern);

    if (result) {
      LOG.trace(String.format("Value [%s] matches pattern [%s]", value, pattern));
      return true;
    }
    return false;
  }
}
