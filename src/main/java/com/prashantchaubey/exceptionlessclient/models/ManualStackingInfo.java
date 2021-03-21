package com.prashantchaubey.exceptionlessclient.models;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Builder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ManualStackingInfo {
  private String title;
  private Map<String, String> signatureData;
}
