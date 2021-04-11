package com.exceptionless.exceptionlessclient.storage;

import com.exceptionless.exceptionlessclient.models.storage.StorageItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class InMemoryStorageTest {
  private InMemoryStorage<Integer> storage;

  @BeforeEach
  public void setup() {
    storage = InMemoryStorage.<Integer>builder().maxItems(2).build();
  }

  @Test
  public void itCanSave() {
    storage.save(123);

    assertThat(storage.peek().getValue()).isEqualTo(123);
  }

  @Test
  public void itCanSaveAndRemoveIfExceedTheMaxItemsSize() {
    storage.save(123);
    storage.save(456);
    storage.save(789);

    assertThat(storage.peek().getValue()).isEqualTo(456);
  }

  @Test
  public void itCanGetItemsForALimitLessThanCurrentItems() {
    storage.save(123);
    storage.save(456);

    List<StorageItem<Integer>> items = storage.get(1);
    assertThat(items.stream().map(StorageItem::getValue).collect(Collectors.toList()))
        .isEqualTo(List.of(123));
  }

  @Test
  public void itCanGetItemsForALimitEqualToCurrentItems() {
    storage.save(123);
    storage.save(456);

    List<StorageItem<Integer>> items = storage.get(2);
    assertThat(items.stream().map(StorageItem::getValue).collect(Collectors.toList()))
        .isEqualTo(List.of(123, 456));
  }

  @Test
  public void itCanGetItemsForALimitMoreThanCurrentItems() {
    storage.save(123);
    storage.save(456);

    List<StorageItem<Integer>> items = storage.get(3);
    assertThat(items.stream().map(StorageItem::getValue).collect(Collectors.toList()))
        .isEqualTo(List.of(123, 456));
  }

  @Test
  public void itCanRemoveByTimestamp() {
    storage.save(123);
    StorageItem<Integer> item = storage.peek();
    storage.remove(item.getTimestamp());

    assertThat(storage.peek()).isNull();
  }
}
