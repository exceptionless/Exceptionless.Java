package com.exceptionless.exceptionlessclient.models.services;

import com.exceptionless.exceptionlessclient.models.base.Model;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Value
@NonFinal
@EqualsAndHashCode(callSuper = true)
public class EnvironmentInfo extends Model {
  private int processorCount;
  private long totalPhysicalMemory;
  private long availablePhysicalMemory;
  private String commandLine;
  private String processName;
  private String processId;
  private long processMemorySize;
  private String threadid;
  private String architecture;
  private String osName;
  private String osVersion;
  private String ipAddress;
  private String machineName;
  private String installId;
  private String runtimeVersion;
}
