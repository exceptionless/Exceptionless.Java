package com.prashantchaubey.exceptionlessclient.services;

import com.prashantchaubey.exceptionlessclient.models.services.EnvironmentInfo;

public interface EnvironmentInfoCollectorIF {
  EnvironmentInfo getEnvironmentInfo(EnvironmentInfoGetArgs args);
}
