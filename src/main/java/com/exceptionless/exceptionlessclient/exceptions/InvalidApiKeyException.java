package com.exceptionless.exceptionlessclient.exceptions;

public class InvalidApiKeyException extends RuntimeException {
  public InvalidApiKeyException(String message) {
    super(message);
  }
}
