package com.exceptionless.exceptionlessclient.submission;

import com.exceptionless.exceptionlessclient.configuration.ValueProvider;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.UserDescription;
import com.exceptionless.exceptionlessclient.settings.SettingsManager;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DefaultSubmissionClientTest {
  @Mock private SettingsManager settingsManager;
  @Mock private OkHttpClient httpClient;
  @Mock private Call call;
  private DefaultSubmissionClient submissionClient;
  private Response.Builder responseBuilder;

  @BeforeEach
  public void setup() {
    submissionClient =
        new DefaultSubmissionClient(
            httpClient,
            settingsManager,
            ValueProvider.of(10),
            ValueProvider.of("http://test-server-url"),
            ValueProvider.of("test-api-key"),
            ValueProvider.of("http://test-heartbeat-server-url"));
    responseBuilder =
        new Response.Builder()
            .request(new Request.Builder().url("http://test-url").build())
            .protocol(Protocol.HTTP_2)
            .body(ResponseBody.create("test-body", MediaType.get("text/plain")))
            .message("test-message")
            .code(200);
    OkHttpClient.Builder mockBuilder = Mockito.mock(OkHttpClient.Builder.class);
    doReturn(mockBuilder).when(httpClient).newBuilder();
    doReturn(mockBuilder).when(mockBuilder).connectTimeout(Duration.ofMillis(10));
    doReturn(httpClient).when(mockBuilder).build();
  }

  @Test
  public void itCanPostEventsSuccessfully() throws IOException {
    Response response =
        responseBuilder.headers(Headers.of(Map.of("x-exceptionless-configversion", "3"))).build();
    doReturn(response).when(call).execute();
    doReturn(call)
        .when(httpClient)
        .newCall(
            argThat(
                request ->
                    request
                            .url()
                            .toString()
                            .equals(
                                "http://test-server-url/api/v2/events?access_token=test-api-key")
                        && request.method().equals("POST")));

    SubmissionResponse submissionResponse =
        submissionClient.postEvents(List.of(Event.builder().build()));

    assertThat(submissionResponse.getBody()).isEqualTo("test-body");
    assertThat(submissionResponse.getCode()).isEqualTo(200);
    verify(settingsManager, times(1)).checkVersion(3);
  }

  @Test
  public void itCanDetectRateLimitingFromHeaders() throws IOException {
    Response response =
        responseBuilder
            .headers(
                Headers.of(
                    Map.of("x-exceptionless-configversion", "3", "x-ratelimit-remaining", "0")))
            .build();
    doReturn(response).when(call).execute();
    doReturn(call)
        .when(httpClient)
        .newCall(
            argThat(
                request ->
                    request
                            .url()
                            .toString()
                            .equals(
                                "http://test-server-url/api/v2/events?access_token=test-api-key")
                        && request.method().equals("POST")));

    SubmissionResponse submissionResponse =
        submissionClient.postEvents(List.of(Event.builder().build()));

    assertThat(submissionResponse.getBody()).isEqualTo("test-body");
    assertThat(submissionResponse.getCode()).isEqualTo(200);
    verify(settingsManager, times(1)).checkVersion(3);
    assertThat(submissionResponse.isRateLimited()).isTrue();
  }

  @Test
  public void itCanPostEventsSuccessfullyWhenNoSettingHeaderIsReturned() throws IOException {
    doReturn(responseBuilder.build()).when(call).execute();
    doReturn(call).when(httpClient).newCall(any());

    SubmissionResponse submissionResponse =
        submissionClient.postEvents(List.of(Event.builder().build()));

    assertThat(submissionResponse.getBody()).isEqualTo("test-body");
    assertThat(submissionResponse.getCode()).isEqualTo(200);
    verifyZeroInteractions(settingsManager);
  }

  @Test
  public void itCanHandleAllExceptionsWhilePostingEvents() {
    Exception exception = new RuntimeException("test");
    doThrow(exception).when(httpClient).newCall(any());

    SubmissionResponse response = submissionClient.postEvents(List.of(Event.builder().build()));

    assertThat(response.hasException()).isTrue();
    assertThat(response.getException()).isSameAs(exception);
  }

  @Test
  public void itCanPostUserDescriptionSuccessfully() throws IOException {
    Response response =
        responseBuilder.headers(Headers.of(Map.of("x-exceptionless-configversion", "3"))).build();
    doReturn(response).when(call).execute();
    doReturn(call)
        .when(httpClient)
        .newCall(
            argThat(
                request ->
                    request
                            .url()
                            .toString()
                            .equals(
                                "http://test-server-url/api/v2/events/by-ref/test-reference-id/user-description?access_token=test-api-key")
                        && request.method().equals("POST")));

    SubmissionResponse submissionResponse =
        submissionClient.postUserDescription(
            "test-reference-id", UserDescription.builder().build());

    assertThat(submissionResponse.getBody()).isEqualTo("test-body");
    assertThat(submissionResponse.getCode()).isEqualTo(200);
    verify(settingsManager, times(1)).checkVersion(3);
  }

  @Test
  public void itCanPostUserDescriptionSuccessfullyWhenNoSettingHeaderIsReturned()
      throws IOException {
    doReturn(responseBuilder.build()).when(call).execute();
    doReturn(call).when(httpClient).newCall(any());

    SubmissionResponse submissionResponse =
        submissionClient.postUserDescription(
            "test-reference-id", UserDescription.builder().build());

    assertThat(submissionResponse.getBody()).isEqualTo("test-body");
    assertThat(submissionResponse.getCode()).isEqualTo(200);
    verifyZeroInteractions(settingsManager);
  }

  @Test
  public void itCanHandleAllExceptionsWhilePostingUserDescription() {
    Exception exception = new RuntimeException("test");
    doThrow(exception).when(httpClient).newCall(any());

    SubmissionResponse response =
        submissionClient.postUserDescription(
            "test-reference-id", UserDescription.builder().build());

    assertThat(response.hasException()).isTrue();
    assertThat(response.getException()).isSameAs(exception);
  }

  @Test
  public void itCanSendHeartbeatSuccessfully() throws IOException {
    doReturn(responseBuilder.build()).when(call).execute();
    doReturn(call)
        .when(httpClient)
        .newCall(
            argThat(
                request ->
                    request
                            .url()
                            .toString()
                            .equals(
                                "http://test-heartbeat-server-url/api/v2/events/session/heartbeat?id=test-user-id&close=true&access_token=test-api-key")
                        && request.method().equals("GET")));

    submissionClient.sendHeartBeat("test-user-id", true);

    verify(call, times(1)).execute();
  }

  @Test
  public void itCanHandleAllExceptionsWhileSendingHeartbeat() {
    doThrow(new RuntimeException("test")).when(httpClient).newCall(any());

    submissionClient.sendHeartBeat("test-user-id", true);
  }
}
