package com.prashantchaubey.exceptionlessclient.submission;

@FunctionalInterface
public interface SubmissionHandler {
  void handle(int status, String message, String data, Object headers);
}
