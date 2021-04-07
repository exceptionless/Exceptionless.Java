package com.exceptionless.exceptionlessclient.services;

import com.exceptionless.exceptionlessclient.models.services.EnvironmentInfo;

public interface EnvironmentInfoCollectorIF {
  EnvironmentInfo getEnvironmentInfo(EnvironmentInfoGetArgs args);
}
