package com.prashantchaubey.exceptionlessclient.services;

import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.models.services.EnvironmentInfo;
import com.prashantchaubey.exceptionlessclient.models.services.EnvironmentInfoGetArgs;
import com.sun.management.OperatingSystemMXBean;
import lombok.Builder;
import lombok.Getter;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

@Builder
@Getter
public class DefaultEnvironmentInfoCollector implements EnvironmentInfoCollectorIF {
  private LogIF log;

  // lombok ignored fields
  private int $processorCount;
  private long $totalPhysicalMemory;
  private String $runtimeVersion;
  private String $osVersion;
  private String $osName;
  private String $architecture;
  private String $processName;
  private String $processId;
  private String $commandLine;

  {
    // One time calculations
    $processorCount = Runtime.getRuntime().availableProcessors();
    OperatingSystemMXBean operatingSystemMXBean =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    $totalPhysicalMemory = operatingSystemMXBean.getTotalPhysicalMemorySize();
    $osName = operatingSystemMXBean.getName();
    $architecture = operatingSystemMXBean.getArch();
    $osVersion = operatingSystemMXBean.getVersion();
    $runtimeVersion = System.getProperty("java.version");
    $processId = String.valueOf(ProcessHandle.current().pid());
    $processName = ManagementFactory.getRuntimeMXBean().getName();
    $commandLine = ProcessHandle.current().info().commandLine().orElse(null);
  }

  @Override
  public EnvironmentInfo getEnvironmentInfo(EnvironmentInfoGetArgs args) {
    OperatingSystemMXBean operatingSystemMXBean =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    EnvironmentInfo.EnvironmentInfoBuilder builder =
        EnvironmentInfo.builder()
            .processorCount($processorCount)
            .totalPhysicalMemory($totalPhysicalMemory)
            .availablePhysicalMemory(operatingSystemMXBean.getFreePhysicalMemorySize())
            .commandLine($commandLine)
            .processName($processName)
            .processId($processId)
            .processMemorySize(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed())
            .threadid(String.valueOf(Thread.currentThread().getId()))
            .architecture($architecture)
            .osName($osName)
            .osVersion($osVersion)
            .runtimeVersion($runtimeVersion)
            .data(getData());

    try {
      InetAddress localhost = InetAddress.getLocalHost();
      if (args.isIncludeMachineName()) {
        builder.machineName(localhost.getHostName());
      }
      if (args.isIncludeIpAddress()) {
        builder.ipAddress(localhost.getHostAddress());
      }
    } catch (UnknownHostException e) {
      log.error("Error while getting machine name", e);
    }

    return builder.build();
  }

  private Map<String, Object> getData() {
    Map<String, Object> data = new HashMap<>();
    OperatingSystemMXBean operatingSystemMXBean =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    data.put("loadavg", String.valueOf(operatingSystemMXBean.getSystemLoadAverage()));
    data.put("tmpdir", System.getProperty("java.io.tmpdir"));
    data.put("uptime", String.valueOf(ManagementFactory.getRuntimeMXBean().getUptime()));
    data.put("endianess", getEndianess());

    return data;
  }

  private String getEndianess() {
    return ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN) ? "Big-endian" : "Little-endian";
  }
}
