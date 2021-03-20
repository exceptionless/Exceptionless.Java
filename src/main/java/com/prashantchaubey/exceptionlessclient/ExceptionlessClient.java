package com.prashantchaubey.exceptionlessclient;

import com.prashantchaubey.exceptionlessclient.configuration.Configuration;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ExceptionlessClient {
  private static ExceptionlessClient INSTANCE;

  public static ExceptionlessClient defaultInstance() {
    if (INSTANCE == null) {
      INSTANCE = ExceptionlessClient.builder().build();
    }

    return INSTANCE;
  }

  private Configuration config;
  //todo I moved it from configuration; check we can remove this
  @Builder.Default private boolean enabled = true;
}
