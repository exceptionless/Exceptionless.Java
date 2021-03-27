package com.prashantchaubey.exceptionlessclient.models.services;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Builder
@Value
@NonFinal
public class EnvironmentInfoGetArgs {
  private boolean includeMachineName;
  private boolean includeIpAddress;
}
