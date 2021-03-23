package com.prashantchaubey.exceptionlessclient.services;

import com.prashantchaubey.exceptionlessclient.models.services.EnvironmentInfo;
import com.prashantchaubey.exceptionlessclient.models.services.EnvironmentInfoGetArgs;

public interface EnvironmentInfoCollectorIF {
  EnvironmentInfo getEnvironmentInfo(EnvironmentInfoGetArgs args);
}
