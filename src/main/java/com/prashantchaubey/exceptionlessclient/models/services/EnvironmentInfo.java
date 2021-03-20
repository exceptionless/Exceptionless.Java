package com.prashantchaubey.exceptionlessclient.models.services;

import com.prashantchaubey.exceptionlessclient.models.base.Model;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class EnvironmentInfo extends Model {
  private int processorCount;
  private int totalPhysicalMemory;
  private int availablePhysicalMemory;
  private String commandLine;
  private String processName;
  private String processId;
  private int processMemorySize;
  private String threadid;
  private String architecture;
  private String osName;
  private String osVersion;
  private String ipAddress;
  private String machineName;
  private String installId;
  private String runtimeVersion;
}
