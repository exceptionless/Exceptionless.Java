package com.exceptionless.exceptionlessclient.exceptions;

public class BadEventDataException extends RuntimeException {
  public BadEventDataException(String message) {
    super(message);
  }
}
