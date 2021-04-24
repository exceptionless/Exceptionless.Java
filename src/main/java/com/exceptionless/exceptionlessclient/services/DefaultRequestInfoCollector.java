package com.exceptionless.exceptionlessclient.services;

import com.exceptionless.exceptionlessclient.models.services.RequestInfo;
import com.exceptionless.exceptionlessclient.utils.Utils;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpRequest;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class DefaultRequestInfoCollector implements RequestInfoCollectorIF {
  @Builder
  public DefaultRequestInfoCollector() {}

  @Override
  public RequestInfo getRequestInfo(HttpRequest request, RequestInfoGetArgs args) {
    RequestInfo.RequestInfoBuilder<?, ?> builder =
        RequestInfo.builder()
            .userAgent(request.headers().firstValue("User-Agent").orElse(null))
            .secure(isSecure(request.uri()))
            .httpMethod(request.method())
            .host(request.uri().getHost())
            .path(request.uri().getPath())
            .port(request.uri().getPort());

    if (args.isIncludeIpAddress()) {
      try {
        InetAddress address = InetAddress.getByName(request.uri().getHost());
        builder.clientIpAddress(address.getHostAddress());
      } catch (UnknownHostException e) {
        log.error(
            String.format("Error while getting ip-address for host: %s", request.uri().getHost()));
      }
    }

    if (args.isIncludeCookies()) {
      builder.cookies(filterExclusions(Utils.getCookies(request), args.getExclusions()));
    }

    if (args.isIncludeQueryString()) {
      builder.queryString(
          filterExclusions(Utils.getQueryParams(request.uri()), args.getExclusions()));
    }

    // todo get post data from request.

    return builder.build();
  }

  private <X> Map<String, X> filterExclusions(Map<String, X> map, Set<String> exclusions) {
    return map.entrySet().stream()
        .filter(
            entry ->
                exclusions.stream().noneMatch(exclusion -> Utils.match(entry.getKey(), exclusion)))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private boolean isSecure(URI uri) {
    return uri.getScheme().contains("https");
  }
}
