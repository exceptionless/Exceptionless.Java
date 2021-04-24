package com.exceptionless.exceptionlessclient.models.services;

import com.exceptionless.exceptionlessclient.models.base.Model;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Value
@EqualsAndHashCode(callSuper = true)
public class EnvironmentInfo extends Model {
  Integer processorCount;
  Long totalPhysicalMemory;
  Long availablePhysicalMemory;
  String commandLine;
  String processName;
  String processId;
  Long processMemorySize;
  String threadId;
  String architecture;
  String osName;
  String osVersion;
  String ipAddress;
  String machineName;
  String installId;
  String runtimeVersion;
}
