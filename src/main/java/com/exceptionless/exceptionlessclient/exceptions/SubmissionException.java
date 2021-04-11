package com.exceptionless.exceptionlessclient.exceptions;

public class SubmissionException extends RuntimeException {
  public SubmissionException(Throwable cause) {
    super(cause);
  }

  public SubmissionException(String message) {
    super(message);
  }
}
