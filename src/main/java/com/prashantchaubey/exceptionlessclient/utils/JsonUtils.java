package com.prashantchaubey.exceptionlessclient.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtils {
  public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private JsonUtils() {}
}
