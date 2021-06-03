package com.exceptionless.exceptionlessclient.configuration;

import lombok.Builder;

@Builder
public class ValueProvider<T> {
  T value;

  public static <X> ValueProvider<X> of(X value) {
    return ValueProvider.<X>builder().value(value).build();
  }

  public void update(T newValue) {
    this.value = newValue;
  }

  public T get() {
    return value;
  }
}
