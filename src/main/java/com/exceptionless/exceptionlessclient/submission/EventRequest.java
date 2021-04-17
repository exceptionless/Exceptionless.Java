package com.exceptionless.exceptionlessclient.submission;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@Value
@Builder
public class EventRequest {
  String type;
  String source;
  LocalDate date;
  Set<String> tags;
  String message;
  String geo;
  String referenceId;
  long value;
  Long count;
  Map<String, Object> data;
}
