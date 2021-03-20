package com.prashantchaubey.exceptionlessclient.storage;

import com.prashantchaubey.exceptionlessclient.models.storage.StorageItem;

import java.util.List;

public interface StorageIF<X> {
  long save(Object value);

  List<StorageItem<X>> get();

  List<StorageItem<X>> get(int limit);

  void remove(long timestamp);

  void clear();
}
