package com.exceptionless.exceptionlessclient.submission;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class UserDescriptionRequest {
  String emailAddress;
  String description;
  Map<String, Object> data;
}
