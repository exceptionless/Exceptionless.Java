package com.exceptionless.exceptionlessclient.settings;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.models.submission.SettingsResponse;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
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
    Configuration configuration =
        TestFixtures.aDefaultConfiguration()
            .serverUrl("http://test-server-url")
            .apiKey("test-api-key")
            .settingsClientTimeoutInMillis(10)
            .build();
    settingsClient = new DefaultSettingsClient(configuration, httpClient);
    responseBuilder =
        new Response.Builder()
            .request(new Request.Builder().url("http://test-url").build())
            .protocol(Protocol.HTTP_2)
            .message("test-message")
            .body(ResponseBody.create("test-body", MediaType.get("text/plain")))
            .code(200);
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

    assertThat(response.getMessage()).isNull();
    assertThat(response.getException()).isNull();
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getSettings())
        .isEqualTo(ServerSettings.builder().version(1L).settings(Map.of("key", "value")).build());
  }

  @Test
  public void itCanHandleAUnsuccessfulResponse() throws IOException {
    doReturn(responseBuilder.code(400).build()).when(call).execute();
    doReturn(call).when(httpClient).newCall(any());

    SettingsResponse response = settingsClient.getSettings(1);

    assertThat(response.getMessage()).isEqualTo("Code: 400, Body: test-body");
    assertThat(response.getException()).isNull();
    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getSettings()).isNull();
  }

  @Test
  public void itCanHandleNullSettingsReturnedByTheServer() throws IOException {
    doReturn(responseBuilder.body(null).build()).when(call).execute();
    doReturn(call).when(httpClient).newCall(any());

    SettingsResponse response = settingsClient.getSettings(1);

    assertThat(response.getMessage()).isEqualTo("No settings returned by server!");
    assertThat(response.getException()).isNull();
    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getSettings()).isNull();
  }

  @Test
  public void itCanHandleAnyException() {
    RuntimeException e = new RuntimeException("test");
    doThrow(e).when(httpClient).newCall(any());

    SettingsResponse response = settingsClient.getSettings(1);

    assertThat(response.getMessage()).isEqualTo("test");
    assertThat(response.getException()).isEqualTo(e);
    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getSettings()).isNull();
  }
}
