package com.prashantchaubey.exceptionlessclient.storage;

import java.util.List;

public interface StorageIF {
    long save(Object value);
    List<StorageItem> get(int limit);
    void remove(long timestamp);
    void clear();
}
