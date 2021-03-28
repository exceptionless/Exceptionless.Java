package com.prashantchaubey.exceptionlessclient.models.storage;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Builder
@Value
@NonFinal
public class StorageItem<X> {
  private long timestamp;
  private X value;
}
