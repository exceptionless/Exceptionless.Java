package com.exceptionless.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class QueueStorageTest {
  @TempDir static Path tempDir;

  static Stream<QueueStorage> storages() throws Exception {
    return Stream.of(
        new InMemoryQueueStorage(5),
        new FileQueueStorage(Files.createTempDirectory(tempDir, "q"), 5));
  }

  @ParameterizedTest
  @MethodSource("storages")
  void peeksOldestFirstWithoutRemoving(QueueStorage storage) {
    storage.enqueue("{\"n\":1}");
    storage.enqueue("{\"n\":2}");
    storage.enqueue("{\"n\":3}");
    List<QueueEntry> entries = storage.peek(2);
    assertThat(entries).extracting(QueueEntry::json).containsExactly("{\"n\":1}", "{\"n\":2}");
    assertThat(storage.size()).isEqualTo(3);
    storage.remove(entries);
    assertThat(storage.peek(10)).extracting(QueueEntry::json).containsExactly("{\"n\":3}");
  }

  @ParameterizedTest
  @MethodSource("storages")
  void tracksAttempts(QueueStorage storage) {
    storage.enqueue("{}");
    storage.incrementAttempts(storage.peek(1));
    storage.incrementAttempts(storage.peek(1));
    assertThat(storage.peek(1).get(0).attempts()).isEqualTo(2);
  }

  @ParameterizedTest
  @MethodSource("storages")
  void dropsOldestWhenFull(QueueStorage storage) {
    for (int i = 0; i < 8; i++) {
      storage.enqueue("{\"n\":" + i + "}");
    }
    assertThat(storage.size()).isEqualTo(5);
    assertThat(storage.peek(1).get(0).json()).isEqualTo("{\"n\":3}");
  }

  @ParameterizedTest
  @MethodSource("storages")
  void clears(QueueStorage storage) {
    storage.enqueue("{}");
    storage.clear();
    assertThat(storage.size()).isZero();
    assertThat(storage.peek(10)).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("storages")
  void isThreadSafe(QueueStorage storage) throws Exception {
    QueueStorage big =
        storage instanceof InMemoryQueueStorage
            ? new InMemoryQueueStorage(10_000)
            : new FileQueueStorage(Files.createTempDirectory(tempDir, "mt"), 10_000);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    int perThread = big instanceof InMemoryQueueStorage ? 500 : 50;
    List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
    for (int t = 0; t < 8; t++) {
      futures.add(
          pool.submit(
              () -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                  big.enqueue("{}");
                  if (i % 10 == 0) {
                    big.remove(big.peek(1));
                  }
                }
                return null;
              }));
    }
    start.countDown();
    for (var f : futures) {
      f.get(30, TimeUnit.SECONDS);
    }
    pool.shutdown();
    // Concurrent peek+remove may remove the same entry twice, so only the bounds are exact.
    assertThat(big.size()).isBetween(8 * perThread - 8 * (perThread / 10), 8 * perThread);
  }

  @org.junit.jupiter.api.Test
  void fileStorageSurvivesRestart() throws Exception {
    Path dir = Files.createTempDirectory(tempDir, "restart");
    FileQueueStorage first = new FileQueueStorage(dir);
    first.enqueue("{\"a\":1}");
    first.enqueue("{\"a\":2}");
    first.incrementAttempts(first.peek(1));
    FileQueueStorage second = new FileQueueStorage(dir);
    List<QueueEntry> entries = second.peek(10);
    assertThat(entries).extracting(QueueEntry::json).containsExactly("{\"a\":1}", "{\"a\":2}");
    assertThat(entries.get(0).attempts()).isEqualTo(1);
    assertThat(entries.get(0).enqueuedAt()).isBeforeOrEqualTo(java.time.Instant.now());
  }

  @org.junit.jupiter.api.Test
  void fileStorageIgnoresForeignAndTempFiles() throws Exception {
    Path dir = Files.createTempDirectory(tempDir, "foreign");
    Files.writeString(dir.resolve("notes.txt"), "x");
    Files.writeString(dir.resolve("123-1.0.json.tmp"), "partial");
    Files.writeString(dir.resolve("bogus.json"), "{}");
    FileQueueStorage storage = new FileQueueStorage(dir);
    storage.enqueue("{\"ok\":true}");
    assertThat(storage.peek(10)).extracting(QueueEntry::json).containsExactly("{\"ok\":true}");
  }

  @org.junit.jupiter.api.Test
  void keyValueStorage() throws Exception {
    Path dir = Files.createTempDirectory(tempDir, "kv");
    FileKeyValueStorage storage = new FileKeyValueStorage(dir);
    assertThat(storage.read("install-id")).isNull();
    storage.write("install-id", "abc");
    assertThat(new FileKeyValueStorage(dir).read("install-id")).isEqualTo("abc");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.write("../escape", "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
