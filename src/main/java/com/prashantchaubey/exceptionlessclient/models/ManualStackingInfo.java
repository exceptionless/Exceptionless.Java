package com.prashantchaubey.exceptionlessclient.models;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Builder
@Getter
public class ManualStackingInfo {
  private String title;
  private Map<String, String> signatureData;
}
