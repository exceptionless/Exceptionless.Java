package com.exceptionless.models;

import com.exceptionless.json.JsonSerializable;
import com.exceptionless.json.JsonWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The machine and process an event came from ({@code @environment}). */
public final class EnvironmentInfo implements JsonSerializable {
  private @Nullable Integer processorCount;
  private @Nullable Long totalPhysicalMemory;
  private @Nullable Long availablePhysicalMemory;
  private @Nullable String commandLine;
  private @Nullable String processName;
  private @Nullable String processId;
  private @Nullable Long processMemorySize;
  private @Nullable String threadName;
  private @Nullable String threadId;
  private @Nullable String architecture;
  private @Nullable String osName;
  private @Nullable String osVersion;
  private @Nullable String ipAddress;
  private @Nullable String machineName;
  private @Nullable String installId;
  private @Nullable String runtimeVersion;
  private final Map<String, Object> data = new LinkedHashMap<>();

  public @Nullable Integer getProcessorCount() {
    return processorCount;
  }

  public EnvironmentInfo setProcessorCount(@Nullable Integer processorCount) {
    this.processorCount = processorCount;
    return this;
  }

  public @Nullable Long getTotalPhysicalMemory() {
    return totalPhysicalMemory;
  }

  public EnvironmentInfo setTotalPhysicalMemory(@Nullable Long totalPhysicalMemory) {
    this.totalPhysicalMemory = totalPhysicalMemory;
    return this;
  }

  public @Nullable Long getAvailablePhysicalMemory() {
    return availablePhysicalMemory;
  }

  public EnvironmentInfo setAvailablePhysicalMemory(@Nullable Long availablePhysicalMemory) {
    this.availablePhysicalMemory = availablePhysicalMemory;
    return this;
  }

  public @Nullable String getCommandLine() {
    return commandLine;
  }

  public EnvironmentInfo setCommandLine(@Nullable String commandLine) {
    this.commandLine = commandLine;
    return this;
  }

  public @Nullable String getProcessName() {
    return processName;
  }

  public EnvironmentInfo setProcessName(@Nullable String processName) {
    this.processName = processName;
    return this;
  }

  public @Nullable String getProcessId() {
    return processId;
  }

  public EnvironmentInfo setProcessId(@Nullable String processId) {
    this.processId = processId;
    return this;
  }

  public @Nullable Long getProcessMemorySize() {
    return processMemorySize;
  }

  public EnvironmentInfo setProcessMemorySize(@Nullable Long processMemorySize) {
    this.processMemorySize = processMemorySize;
    return this;
  }

  public @Nullable String getThreadName() {
    return threadName;
  }

  public EnvironmentInfo setThreadName(@Nullable String threadName) {
    this.threadName = threadName;
    return this;
  }

  public @Nullable String getThreadId() {
    return threadId;
  }

  public EnvironmentInfo setThreadId(@Nullable String threadId) {
    this.threadId = threadId;
    return this;
  }

  public @Nullable String getArchitecture() {
    return architecture;
  }

  public EnvironmentInfo setArchitecture(@Nullable String architecture) {
    this.architecture = architecture;
    return this;
  }

  public @Nullable String getOsName() {
    return osName;
  }

  public EnvironmentInfo setOsName(@Nullable String osName) {
    this.osName = osName;
    return this;
  }

  public @Nullable String getOsVersion() {
    return osVersion;
  }

  public EnvironmentInfo setOsVersion(@Nullable String osVersion) {
    this.osVersion = osVersion;
    return this;
  }

  public @Nullable String getIpAddress() {
    return ipAddress;
  }

  public EnvironmentInfo setIpAddress(@Nullable String ipAddress) {
    this.ipAddress = ipAddress;
    return this;
  }

  public @Nullable String getMachineName() {
    return machineName;
  }

  public EnvironmentInfo setMachineName(@Nullable String machineName) {
    this.machineName = machineName;
    return this;
  }

  public @Nullable String getInstallId() {
    return installId;
  }

  public EnvironmentInfo setInstallId(@Nullable String installId) {
    this.installId = installId;
    return this;
  }

  public @Nullable String getRuntimeVersion() {
    return runtimeVersion;
  }

  public EnvironmentInfo setRuntimeVersion(@Nullable String runtimeVersion) {
    this.runtimeVersion = runtimeVersion;
    return this;
  }

  public Map<String, Object> getData() {
    return data;
  }

  @Override
  public void toJson(JsonWriter writer) {
    writer.beginObject();
    writer.fieldIfNotEmpty("processor_count", processorCount);
    writer.fieldIfNotEmpty("total_physical_memory", totalPhysicalMemory);
    writer.fieldIfNotEmpty("available_physical_memory", availablePhysicalMemory);
    writer.fieldIfNotEmpty("command_line", commandLine);
    writer.fieldIfNotEmpty("process_name", processName);
    writer.fieldIfNotEmpty("process_id", processId);
    writer.fieldIfNotEmpty("process_memory_size", processMemorySize);
    writer.fieldIfNotEmpty("thread_name", threadName);
    writer.fieldIfNotEmpty("thread_id", threadId);
    writer.fieldIfNotEmpty("architecture", architecture);
    writer.fieldIfNotEmpty("o_s_name", osName);
    writer.fieldIfNotEmpty("o_s_version", osVersion);
    writer.fieldIfNotEmpty("ip_address", ipAddress);
    writer.fieldIfNotEmpty("machine_name", machineName);
    writer.fieldIfNotEmpty("install_id", installId);
    writer.fieldIfNotEmpty("runtime_version", runtimeVersion);
    writer.fieldIfNotEmpty("data", data);
    writer.endObject();
  }
}
