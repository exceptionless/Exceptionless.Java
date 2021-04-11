package com.exceptionless.exceptionlessclient.services;

import com.exceptionless.exceptionlessclient.models.services.RequestInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultRequestInfoCollectorTest {
  private DefaultRequestInfoCollector defaultRequestInfoCollector;

  @BeforeEach
  public void setup() {
    defaultRequestInfoCollector = DefaultRequestInfoCollector.builder().build();
  }

  @Test
  public void itCanGetARequestInfoFromAHttpRequest() {
    HttpRequest httpRequest =
        HttpRequest.newBuilder().uri(URI.create("http://localhost:5000/test-path")).GET().build();

    RequestInfo requestInfo =
        defaultRequestInfoCollector.getRequestInfo(
            httpRequest, RequestInfoGetArgs.builder().build());

    assertThat(requestInfo.getUserAgent()).isNull();
    assertThat(requestInfo.isSecure()).isFalse();
    assertThat(requestInfo.getHttpMethod()).isEqualTo("GET");
    assertThat(requestInfo.getHost()).isEqualTo("localhost");
    assertThat(requestInfo.getPath()).isEqualTo("/test-path");
    assertThat(requestInfo.getPort()).isEqualTo(5000);
  }

  @Test
  public void itCanGetAnUserAgentFromAHttpRequest() {
    HttpRequest httpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:5000/test-path"))
            .GET()
            .header("User-Agent", "test-user-agent")
            .build();

    RequestInfo requestInfo =
        defaultRequestInfoCollector.getRequestInfo(
            httpRequest, RequestInfoGetArgs.builder().build());

    assertThat(requestInfo.getUserAgent()).isEqualTo("test-user-agent");
  }

  @Test
  public void itCanIdentifyASecuredUri() {
    HttpRequest httpRequest =
        HttpRequest.newBuilder().uri(URI.create("https://localhost:5000/test-path")).GET().build();

    RequestInfo requestInfo =
        defaultRequestInfoCollector.getRequestInfo(
            httpRequest, RequestInfoGetArgs.builder().build());

    assertThat(requestInfo.isSecure()).isTrue();
  }

  @Test
  public void itShouldNotIncludeIpAddressCookiesQueryStringByDefault() {
    HttpRequest httpRequest =
        HttpRequest.newBuilder().uri(URI.create("https://localhost:5000/test-path")).GET().build();

    RequestInfo requestInfo =
        defaultRequestInfoCollector.getRequestInfo(
            httpRequest, RequestInfoGetArgs.builder().build());

    assertThat(requestInfo.getClientIpAddress()).isNull();
    assertThat(requestInfo.getCookies()).isEmpty();
    assertThat(requestInfo.getQueryString()).isEmpty();
  }

  @Test
  public void itCanGetIpAddressCookiesAndQueryStringFromAHttpRequest() {
    HttpRequest httpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create("https://localhost:5000/test-path?query-param-key=query-param-value"))
            .header("Cookie", "cookie1=value1;cookie2=value2")
            .GET()
            .build();

    RequestInfo requestInfo =
        defaultRequestInfoCollector.getRequestInfo(
            httpRequest,
            RequestInfoGetArgs.builder()
                .includeCookies(true)
                .includeIpAddress(true)
                .includeQueryString(true)
                .build());

    assertThat(requestInfo.getClientIpAddress()).isNotNull();
    assertThat(requestInfo.getCookies())
        .isEqualTo(Map.of("cookie1", "value1", "cookie2", "value2"));
    assertThat(requestInfo.getQueryString())
        .isEqualTo(Map.of("query-param-key", List.of("query-param-value")));
  }

  @Test
  public void itCanExcludeData() {
    HttpRequest httpRequest =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "https://localhost:5000/test-path?query-param-key=query-param-value&exclude-query-param=exclude-value"))
            .header("Cookie", "cookie1=value1;cookie2=value2;exclude-cookie=exclude-value")
            .GET()
            .build();

    RequestInfo requestInfo =
        defaultRequestInfoCollector.getRequestInfo(
            httpRequest,
            RequestInfoGetArgs.builder()
                .includeCookies(true)
                .includeIpAddress(true)
                .includeQueryString(true)
                .exclusions(Set.of("exclude-query-param", "exclude-cookie"))
                .build());

    assertThat(requestInfo.getClientIpAddress()).isNotNull();
    assertThat(requestInfo.getCookies())
        .isEqualTo(Map.of("cookie1", "value1", "cookie2", "value2"));
    assertThat(requestInfo.getQueryString())
        .isEqualTo(Map.of("query-param-key", List.of("query-param-value")));
  }
}
