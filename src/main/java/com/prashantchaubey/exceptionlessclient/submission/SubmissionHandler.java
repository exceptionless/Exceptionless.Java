package com.prashantchaubey.exceptionlessclient.submission;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface SubmissionHandler {
  void handle(int status, String message, String data, Map<String, List<String>> headers);
}
