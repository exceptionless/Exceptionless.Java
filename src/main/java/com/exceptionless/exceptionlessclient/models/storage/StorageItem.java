package com.exceptionless.exceptionlessclient.models.storage;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class StorageItem<X> {
  long timestamp;
  X value;
}
