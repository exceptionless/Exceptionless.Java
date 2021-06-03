package com.exceptionless.exceptionlessclient.plugins.preconfigured.args;

import lombok.Builder;
import lombok.Value;

import java.util.HashSet;
import java.util.Set;

@Builder
@Value
public class RequestInfoGetArgs {
  @Builder.Default Boolean includeIpAddress = false;
  @Builder.Default Boolean includeCookies = false;
  @Builder.Default Boolean includeQueryString = false;
  @Builder.Default Boolean includePostData = false;
  @Builder.Default Set<String> exclusions = new HashSet<>();

  public Boolean isIncludeIpAddress() {
    return includeIpAddress;
  }

  public Boolean isIncludeCookies() {
    return includeCookies;
  }

  public Boolean isIncludeQueryString() {
    return includeQueryString;
  }
}
