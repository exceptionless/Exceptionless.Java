package com.exceptionless.sample.app;

import com.exceptionless.exceptionlessclient.ExceptionlessClient;

public class Main {
  public static void main(String[] args) {
    ExceptionlessClient client =
        ExceptionlessClient.from(
            System.getenv("EXCEPTIONLESS_SAMPLE_APP_API_KEY"),
            System.getenv("EXCEPTIONLESS_SAMPLE_APP_SERVER_URL"));

    client.submitSessionStart();

    client.submitException(new RuntimeException("Test exception"));
    client.submitUnhandledException(new RuntimeException("Test exception"),"Test submission method");
    client.submitFeatureUsage("Test feature");
    client.submitLog("Test log");
    client.submitNotFound("Test resource");

    client.submitSessionEnd("Test user id");
  }
}
