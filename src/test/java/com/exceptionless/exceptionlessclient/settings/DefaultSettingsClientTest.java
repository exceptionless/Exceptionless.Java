package com.exceptionless.exceptionlessclient.settings;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.models.settings.ServerSettings;
import com.exceptionless.exceptionlessclient.models.submission.SettingsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
public class DefaultSettingsClientTest {
  @Mock private HttpClient httpClient;
  @Mock private HttpResponse<String> response;
  private DefaultSettingsClient settingsClient;

  @BeforeEach
  public void setup() {
    Configuration configuration =
        TestFixtures.aDefaultConfiguration()
            .serverUrl("http://test-server-url")
            .apiKey("test-api-key")
            .settingsClientTimeoutInMillis(10)
            .build();
    settingsClient = new DefaultSettingsClient(configuration, httpClient);
  }

  @Test
  public void itCanHandleASuccessfulResponse() throws IOException, InterruptedException {
    doReturn(response)
        .when(httpClient)
        .send(
            argThat(
                httpRequest ->
                    httpRequest.method().equals("GET")
                        && httpRequest.timeout().isPresent()
                        && httpRequest.timeout().get().equals(Duration.ofMillis(10))
                        && httpRequest.headers().firstValue("X-Exceptionless-Client").isPresent()
                        && httpRequest
                            .headers()
                            .firstValue("X-Exceptionless-Client")
                            .get()
                            .equals("exceptionless-java")
                        && httpRequest
                            .uri()
                            .toString()
                            .equals(
                                "http://test-server-url/api/v2/projects/config?v=1&access_token=test-api-key")),
            any());
    doReturn(
            "{\n"
                + "\t\"version\":1,\n"
                + "\t\"settings\":{\n"
                + "\t\t\"key\":\"value\"\n"
                + "\t}\n"
                + "}")
        .when(response)
        .body();
    doReturn(200).when(response).statusCode();

    SettingsResponse response = settingsClient.getSettings(1);

    assertThat(response.getMessage()).isNull();
    assertThat(response.getException()).isNull();
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getSettings())
        .isEqualTo(ServerSettings.builder().version(1L).settings(Map.of("key", "value")).build());
  }

  @Test
  public void itCanHandleAUnsuccessfulResponse() throws IOException, InterruptedException {
    doReturn(response).when(httpClient).send(any(), any());
    doReturn("test-response").when(response).body();
    doReturn(400).when(response).statusCode();

    SettingsResponse response = settingsClient.getSettings(1);

    assertThat(response.getMessage()).isEqualTo("test-response");
    assertThat(response.getException()).isNull();
    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getSettings()).isNull();
  }

  @Test
  public void itCanHandleAnyException() throws IOException, InterruptedException {
    RuntimeException e = new RuntimeException("test");
    doThrow(e).when(httpClient).send(any(), any());

    SettingsResponse response = settingsClient.getSettings(1);

    assertThat(response.getMessage()).isEqualTo("test");
    assertThat(response.getException()).isEqualTo(e);
    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getSettings()).isNull();
  }
}
