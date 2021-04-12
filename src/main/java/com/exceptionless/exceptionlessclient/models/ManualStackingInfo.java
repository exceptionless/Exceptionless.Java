package com.exceptionless.exceptionlessclient.models;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.Map;

@Builder
@Value
@NonFinal
public class ManualStackingInfo {
  String title;
  Map<String, String> signatureData;
}
