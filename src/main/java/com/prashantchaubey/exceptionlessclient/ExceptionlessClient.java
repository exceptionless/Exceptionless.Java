package com.prashantchaubey.exceptionlessclient;

import com.prashantchaubey.exceptionlessclient.configuration.Configuration;
import lombok.Builder;
import lombok.Getter;

@Builder(builderClassName = "ExceptionlessClientInternalBuilder")
@Getter
public class ExceptionlessClient {
  private Configuration config;
  // todo I moved it from configuration; check we can remove this
  @Builder.Default private boolean enabled = true;

  public static ExceptionlessClient from(String apiKey, String serverUrl) {
    return ExceptionlessClient.builder().config(Configuration.from(apiKey, serverUrl)).build();
  }

  private void init() {}

  public static ExceptionlessClientBuilder builder() {
    return new ExceptionlessClientBuilder();
  }

  public static class ExceptionlessClientBuilder extends ExceptionlessClientInternalBuilder {
    ExceptionlessClientBuilder() {
      super();
    }

    @Override
    public ExceptionlessClient build() {
      ExceptionlessClient client = super.build();
      client.init();

      return client;
    }
  }
}
