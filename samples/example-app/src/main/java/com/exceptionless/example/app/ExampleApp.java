package com.exceptionless.example.app;

import com.exceptionless.exceptionlessclient.ExceptionlessClient;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;

public class ExampleApp {
  private static final ExceptionlessClient client =
      ExceptionlessClient.from(
          System.getenv("EXCEPTIONLESS_SAMPLE_APP_API_KEY"),
          System.getenv("EXCEPTIONLESS_SAMPLE_APP_SERVER_URL"));

  public static void sampleEventSubmissions() {
    client.submitException(new RuntimeException("Test exception"));
    client.submitUnhandledException(
        new RuntimeException("Test exception"), "Test submission method");
    client.submitFeatureUsage("Test feature");
    client.submitLog("Test log");
    client.submitNotFound("Test resource");
  }

  public static void sampleUseOfSessions() {
    client.getConfiguration().useSessions();
    client.submitEvent(client.createSessionStart().userIdentity("test-user").build());
    client.submitSessionEnd("test-user");
  }

  public static void sampleUseOfUpdatingEmailAndDescription() {
    client.submitEvent(client.createLog("test-log").referenceId("test-reference-id").build());
    client.updateEmailAndDescription("test-reference-id", "test@email.com", "test-description");
  }

  public static void main(String[] args) {
    sampleEventSubmissions();
    sampleUseOfUpdatingEmailAndDescription();
    sampleUseOfSessions();
  }
}
