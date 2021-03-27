package com.prashantchaubey.exceptionlessclient.models.services;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class EnvironmentInfoGetArgs {
  private boolean includeMachineName;
  private boolean includeIpAddress;
}
