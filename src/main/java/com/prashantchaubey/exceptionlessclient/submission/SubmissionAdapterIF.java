package com.prashantchaubey.exceptionlessclient.submission;

public interface SubmissionAdapterIF {
  void sendRequest(SubmissionRequest request, SubmissionHandler handler, boolean isAppExiting);
}
