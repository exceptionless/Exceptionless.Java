package com.exceptionless.exceptionlessclient.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest {
  @Test
  public void itCanSafelyGetANull() {
    //noinspection ConstantConditions
    assertThat(Utils.safeGetAs(null, String.class)).isNull();
  }

  @Test
  public void itCanSafelyGetANotCastableValue() {
    assertThat(Utils.safeGetAs(1L, String.class)).isNull();
  }

  @Test
  public void itCanSafelyGetAValue() {
    assertThat(Utils.safeGetAs("abc", String.class)).isEqualTo("abc");
  }

  @Test
  public void itCanGetEmptyCookiesIfCookieHeaderIsNotPresent() {
    assertThat(
            Utils.getCookies(
                HttpRequest.newBuilder().uri(URI.create("http://localhost:5000")).build()))
        .isEmpty();
  }

  @Test
  public void itCanGetCookiesFromTheHeader() {
    assertThat(
            Utils.getCookies(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:5000"))
                    .header("Cookie", "cookie1=value1;cookie2=value2")
                    .build()))
        .isEqualTo(Map.of("cookie1", "value1", "cookie2", "value2"));
  }

  @Test
  public void itCanGetEmptyQueryParams() {
    assertThat(Utils.getQueryParams(URI.create("http://localhost:5000"))).isEmpty();
    assertThat(Utils.getQueryParams(URI.create("http://localhost:5000?"))).isEmpty();
  }

  @Test
  public void itCanGetQueryParams() {
    assertThat(
            Utils.getQueryParams(URI.create("http://localhost:5000?param1=value1&param2=value2")))
        .isEqualTo(Map.of("param1", List.of("value1"), "param2", List.of("value2")));
  }

  @Test
  public void itCanGetArrayQueryParams() {
    assertThat(
            Utils.getQueryParams(URI.create("http://localhost:5000?param1=value1&param1=value2")))
        .isEqualTo(Map.of("param1", List.of("value1", "value2")));
  }

  @Test
  public void itDontMatchForNullValue() {
    assertThat(Utils.match(null, "abc")).isFalse();
  }

  @Test
  public void itDontMatchForNullPattern() {
    assertThat(Utils.match("abc", null)).isFalse();
  }

  @Test
  public void itCanMatchAValueToAPattern() {
    assertThat(Utils.match("abc", "def")).isFalse();
    assertThat(Utils.match("ABC", "abc")).isTrue();
  }

  @Test
  public void itCanSerializeEmptyBeans() throws JsonProcessingException {
    class EmptyBean {}

    assertThat(Utils.JSON_MAPPER.writeValueAsString(new EmptyBean())).isEqualTo("{}");
  }
}
