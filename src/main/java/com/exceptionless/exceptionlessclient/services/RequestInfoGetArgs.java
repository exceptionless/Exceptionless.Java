package com.exceptionless.exceptionlessclient.services;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.Set;

@Builder
@Value
@NonFinal
public class RequestInfoGetArgs {
  private boolean includeIpAddress;
  private boolean includeCookies;
  private boolean includeQueryString;
  private boolean includePostData;
  private Set<String> exclusions;
}
