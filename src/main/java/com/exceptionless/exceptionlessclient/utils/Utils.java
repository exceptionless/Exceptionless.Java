package com.exceptionless.exceptionlessclient.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.*;

public final class Utils {
  public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

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
    if (value == null) {
      // todo remove
      LOG.info("Null received for value");
      return false;
    }
    if (pattern == null) {
      // todo remove
      LOG.info("Null received for pattern");
      return false;
    }
    // todo check this works or not;
    boolean result = value.toLowerCase().matches(pattern);
    if (result) {
      // todo remove
      LOG.info(String.format("Value [%s] matches pattern [%s]", value, pattern));
      return true;
    }
    return false;
  }
}
