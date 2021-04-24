package com.exceptionless.exceptionlessclient.services;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class EnvironmentInfoGetArgs {
  @Builder.Default Boolean includeMachineName = false;
  @Builder.Default Boolean includeIpAddress = false;

  public Boolean isIncludeMachineName() {
    return includeIpAddress;
  }

  public Boolean isIncludeIpAddress() {
    return includeIpAddress;
  }
}
