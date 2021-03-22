package com.prashantchaubey.exceptionlessclient.storage;

import com.prashantchaubey.exceptionlessclient.models.storage.StorageItem;
import lombok.Builder;
import lombok.Getter;

import java.util.*;

@Builder
@Getter
public class InMemoryStorage<X> implements StorageIF<X> {
  private int maxItems;

  // lombok ignored fields
  // Used linked list because we are always appending at end and removing is fast than `ArrayList`
  private List<StorageItem<X>> $items = new LinkedList<>();
  private long $lastTimestamp;

  @Override
  public long save(X value) {
    long timestamp = Math.max($lastTimestamp, System.currentTimeMillis());
    $items.add(StorageItem.<X>builder().value(value).timestamp(timestamp).build());
    if ($items.size() > maxItems) {
      $items.remove(0);
    }

    return $lastTimestamp = timestamp;
  }

  @Override
  public StorageItem<X> peek() {
    return !$items.isEmpty() ? $items.get(0) : null;
  }

  @Override
  public List<StorageItem<X>> get(int limit) {
    return $items.subList(0, Math.max(limit, $items.size()));
  }

  @Override
  public void remove(long timestamp) {
    for (int i = 0; i < $items.size(); i++) {
      if ($items.get(i).getTimestamp() == timestamp) {
        $items.remove(i);
        return;
      }
    }
  }

  @Override
  public void clear() {
    $items = new ArrayList<>();
  }
}
