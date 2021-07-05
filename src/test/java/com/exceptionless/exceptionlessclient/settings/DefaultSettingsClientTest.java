package com.exceptionless.exceptionlessclient.settings;

import com.exceptionless.exceptionlessclient.configuration.ValueProvider;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
public class DefaultSettingsClientTest {
  @Mock private OkHttpClient httpClient;
  @Mock private Call call;
  private DefaultSettingsClient settingsClient;
  private Response.Builder responseBuilder;

  @BeforeEach
  public void setup() {
    settingsClient =
        new DefaultSettingsClient(
            httpClient,
            ValueProvider.of(10),
            ValueProvider.of("http://test-server-url"),
            ValueProvider.of("test-api-key"));
    responseBuilder =
        new Response.Builder()
            .request(new Request.Builder().url("http://test-url").build())
            .protocol(Protocol.HTTP_2)
            .message("test-message")
            .body(ResponseBody.create("test-body", MediaType.get("text/plain")))
            .code(200);

    OkHttpClient.Builder mockBuilder = Mockito.mock(OkHttpClient.Builder.class);
    doReturn(mockBuilder).when(httpClient).newBuilder();
    doReturn(mockBuilder).when(mockBuilder).connectTimeout(Duration.ofMillis(10));
    doReturn(httpClient).when(mockBuilder).build();
  }

  @Test
  public void itCanHandleASuccessfulResponse() throws IOException {
    doReturn(
            responseBuilder
                .body(
                    ResponseBody.create(
                        "{\n"
                            + "\t\"version\":1,\n"
                            + "\t\"settings\":{\n"
                            + "\t\t\"key\":\"value\"\n"
                            + "\t}\n"
                            + "}",
                        MediaType.get("application/json")))
                .build())
        .when(call)
        .execute();
    doReturn(call)
        .when(httpClient)
        .newCall(
            argThat(
                httpRequest ->
                    httpRequest.method().equals("GET")
                        && httpRequest
                            .url()
                            .toString()
                            .equals(
                                "http://test-server-url/api/v2/projects/config?v=1&access_token=test-api-key")));

    SettingsResponse response = settingsClient.getSettings(1);

    assertThat(response.getBody())
        .isEqualTo(
            "{\n"
                + "\t\"version\":1,\n"
                + "\t\"settings\":{\n"
                + "\t\t\"key\":\"value\"\n"
                + "\t}\n"
                + "}");
    assertThat(response.getCode()).isEqualTo(200);
    assertThat(response.getSettings())
        .isEqualTo(ServerSettings.builder().version(1L).settings(Map.of("key", "value")).build());
  }

  @Test
  public void itCanHandleNullSettingsReturnedByTheServer() throws IOException {
    doReturn(responseBuilder.body(null).build()).when(call).execute();
    doReturn(call).when(httpClient).newCall(any());

    SettingsResponse response = settingsClient.getSettings(1);

    assertThat(response.getBody()).isEqualTo("");
    assertThat(response.getCode()).isEqualTo(200);
    assertThat(response.getSettings()).isNull();
  }

  @Test
  public void itCanHandleAllExceptions() {
    Exception e = new RuntimeException("test");
    doThrow(e).when(httpClient).newCall(any());

    SettingsResponse response = settingsClient.getSettings(1);

    assertThat(response.hasException()).isTrue();
    assertThat(response.getException()).isSameAs(e);
  }
}
