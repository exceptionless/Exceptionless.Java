package com.exceptionless.exceptionlessclient.submission;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.exceptions.SubmissionException;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.UserDescription;
import com.exceptionless.exceptionlessclient.models.submission.SubmissionResponse;
import com.exceptionless.exceptionlessclient.settings.SettingsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DefaultSubmissionClientTest {
  @Mock private SettingsManager settingsManager;
  @Mock private HttpClient httpClient;
  @Mock private HttpResponse<String> httpResponse;
  private DefaultSubmissionClient submissionClient;

  @BeforeEach
  public void setup() {
    Configuration configuration =
        TestFixtures.aDefaultConfiguration()
            .serverUrl("http://test-server-url")
            .heartbeatServerUrl("http://test-heartbeat-server-url")
            .apiKey("test-api-key")
            .submissionClientTimeoutInMillis(10)
            .build();

    submissionClient = new DefaultSubmissionClient(configuration, settingsManager, httpClient);
  }

  @Test
  public void itCanPostEventsSuccessfully() throws IOException, InterruptedException {
    doReturn("test-body").when(httpResponse).body();
    doReturn(200).when(httpResponse).statusCode();
    doReturn(HttpHeaders.of(Map.of("x-exceptionless-configversion", List.of("3")), (s, s2) -> true))
        .when(httpResponse)
        .headers();
    doReturn(httpResponse)
        .when(httpClient)
        .send(
            argThat(
                request ->
                    request
                            .uri()
                            .toString()
                            .equals(
                                "http://test-server-url/api/v2/events?access_token=test-api-key")
                        && request.method().equals("POST")
                        && request.headers().firstValue("Content-Type").isPresent()
                        && request
                            .headers()
                            .firstValue("Content-Type")
                            .get()
                            .equals("application/json")
                        && request.headers().firstValue("X-Exceptionless-Client").isPresent()
                        && request
                            .headers()
                            .firstValue("X-Exceptionless-Client")
                            .get()
                            .equals("exceptionless-java")
                        && request.timeout().isPresent()
                        && request.timeout().get().equals(Duration.ofMillis(10))),
            any());

    SubmissionResponse submissionResponse =
        submissionClient.postEvents(List.of(Event.builder().build()));

    assertThat(submissionResponse.getMessage()).isEqualTo("test-body");
    assertThat(submissionResponse.getStatusCode()).isEqualTo(200);
    verify(settingsManager, times(1)).checkVersion(3);
  }

  @Test
  public void itCanPostEventsSuccessfullyWhenNoSettingHeaderIsReturned()
      throws IOException, InterruptedException {
    doReturn("test-body").when(httpResponse).body();
    doReturn(200).when(httpResponse).statusCode();
    doReturn(HttpHeaders.of(Map.of(), (s, s2) -> false)).when(httpResponse).headers();
    doReturn(httpResponse).when(httpClient).send(any(), any());

    SubmissionResponse submissionResponse =
        submissionClient.postEvents(List.of(Event.builder().build()));

    assertThat(submissionResponse.getMessage()).isEqualTo("test-body");
    assertThat(submissionResponse.getStatusCode()).isEqualTo(200);
    verifyZeroInteractions(settingsManager);
  }

  @Test
  public void itCanThrowAllExceptionsAsSubmissionExceptionWhilePostingEvents()
      throws IOException, InterruptedException {
    doThrow(new RuntimeException("test")).when(httpClient).send(any(), any());

    assertThatThrownBy(() -> submissionClient.postEvents(List.of(Event.builder().build())))
        .isInstanceOf(SubmissionException.class)
        .hasMessage("java.lang.RuntimeException: test");
  }

  @Test
  public void itCanPostUserDescriptionSuccessfully() throws IOException, InterruptedException {
    doReturn("test-body").when(httpResponse).body();
    doReturn(200).when(httpResponse).statusCode();
    doReturn(HttpHeaders.of(Map.of("x-exceptionless-configversion", List.of("3")), (s, s2) -> true))
        .when(httpResponse)
        .headers();
    doReturn(httpResponse)
        .when(httpClient)
        .send(
            argThat(
                request ->
                    request
                            .uri()
                            .toString()
                            .equals(
                                "http://test-server-url/api/v2/events/by-ref/test-reference-id/user-description?access_token=test-api-key")
                        && request.method().equals("POST")
                        && request.headers().firstValue("Content-Type").isPresent()
                        && request
                            .headers()
                            .firstValue("Content-Type")
                            .get()
                            .equals("application/json")
                        && request.headers().firstValue("X-Exceptionless-Client").isPresent()
                        && request
                            .headers()
                            .firstValue("X-Exceptionless-Client")
                            .get()
                            .equals("exceptionless-java")
                        && request.timeout().isPresent()
                        && request.timeout().get().equals(Duration.ofMillis(10))),
            any());

    SubmissionResponse submissionResponse =
        submissionClient.postUserDescription(
            "test-reference-id", UserDescription.builder().build());

    assertThat(submissionResponse.getMessage()).isEqualTo("test-body");
    assertThat(submissionResponse.getStatusCode()).isEqualTo(200);
    verify(settingsManager, times(1)).checkVersion(3);
  }

  @Test
  public void itCanPostUserDescriptionSuccessfullyWhenNoSettingHeaderIsReturned()
      throws IOException, InterruptedException {
    doReturn("test-body").when(httpResponse).body();
    doReturn(200).when(httpResponse).statusCode();
    doReturn(HttpHeaders.of(Map.of(), (s, s2) -> false)).when(httpResponse).headers();
    doReturn(httpResponse).when(httpClient).send(any(), any());

    SubmissionResponse submissionResponse =
        submissionClient.postUserDescription(
            "test-reference-id", UserDescription.builder().build());

    assertThat(submissionResponse.getMessage()).isEqualTo("test-body");
    assertThat(submissionResponse.getStatusCode()).isEqualTo(200);
    verifyZeroInteractions(settingsManager);
  }

  @Test
  public void itCanThrowAllExceptionsAsSubmissionExceptionWhilePostingUserDescription()
      throws IOException, InterruptedException {
    doThrow(new RuntimeException("test")).when(httpClient).send(any(), any());

    assertThatThrownBy(
            () ->
                submissionClient.postUserDescription(
                    "test-reference-id", UserDescription.builder().build()))
        .isInstanceOf(SubmissionException.class)
        .hasMessage("java.lang.RuntimeException: test");
  }

  @Test
  public void itCanSendHeartbeatSuccessfully() throws IOException, InterruptedException {
    doReturn(200).when(httpResponse).statusCode();
    doReturn(httpResponse).when(httpClient).send(any(), any());

    submissionClient.sendHeartBeat("test-user-id", true);

    verify(httpClient, times(1))
        .send(
            argThat(
                request ->
                    request
                            .uri()
                            .toString()
                            .equals(
                                "http://test-heartbeat-server-url/api/v2/events/session/heartbeat?id=test-user-id&close=true&access_token=test-api-key")
                        && request.method().equals("GET")
                        && request.timeout().isPresent()
                        && request.timeout().get().equals(Duration.ofMillis(10))
                        && request.headers().firstValue("X-Exceptionless-Client").isPresent()
                        && request
                            .headers()
                            .firstValue("X-Exceptionless-Client")
                            .get()
                            .equals("exceptionless-java")),
            any());
  }

  @Test
  public void itCanThrowAllExceptionsAsSubmissionExceptionWhileSendingHeartbeat()
      throws IOException, InterruptedException {
    doThrow(new RuntimeException("test")).when(httpClient).send(any(), any());

    assertThatThrownBy(() -> submissionClient.sendHeartBeat("test-user-id", true))
        .isInstanceOf(SubmissionException.class)
        .hasMessage("java.lang.RuntimeException: test");
  }
}
