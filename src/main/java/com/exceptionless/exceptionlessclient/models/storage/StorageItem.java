package com.exceptionless.exceptionlessclient.models.storage;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Builder
@Value
@NonFinal
public class StorageItem<X> {
  long timestamp;
  X value;
}
