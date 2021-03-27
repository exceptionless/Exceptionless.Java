package com.prashantchaubey.exceptionlessclient.models.services;

import com.prashantchaubey.exceptionlessclient.models.base.Model;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
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
