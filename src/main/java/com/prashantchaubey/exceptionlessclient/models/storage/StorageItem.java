package com.prashantchaubey.exceptionlessclient.models.storage;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class StorageItem<X> {
  private long timestamp;
  private X value;
}
