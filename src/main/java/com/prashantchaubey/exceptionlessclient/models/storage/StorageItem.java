package com.prashantchaubey.exceptionlessclient.models.storage;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Builder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class StorageItem<X> {
  private long timestamp;
  private X value;
}
