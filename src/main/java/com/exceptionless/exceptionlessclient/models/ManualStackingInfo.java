package com.exceptionless.exceptionlessclient.models;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.Map;

@Builder
@Value
@NonFinal
public class ManualStackingInfo {
  private String title;
  private Map<String, String> signatureData;
}
