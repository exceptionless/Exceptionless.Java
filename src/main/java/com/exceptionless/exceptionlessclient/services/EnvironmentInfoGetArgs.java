package com.exceptionless.exceptionlessclient.services;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Builder
@Value
@NonFinal
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
