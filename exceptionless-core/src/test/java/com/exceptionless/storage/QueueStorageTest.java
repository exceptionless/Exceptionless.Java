package com.exceptionless.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exceptionless.TestClock;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class QueueStorageTest {
  enum Kind {
    MEMORY,
    FILE;

    QueueStorage create(Path dir, int maxItems) {
      return create(dir, maxItems, InstantSource.system());
    }

    QueueStorage create(Path dir, int maxItems, InstantSource clock) {
      return this == MEMORY
          ? new InMemoryQueueStorage(maxItems, clock)
          : new FileQueueStorage(dir, maxItems, clock);
    }
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void peeksOldestFirstWithoutRemoving(Kind kind, @TempDir Path dir) {
    QueueStorage storage = kind.create(dir, 5);
    storage.enqueue("{\"n\":1}");
    storage.enqueue("{\"n\":2}");
    storage.enqueue("{\"n\":3}");
    List<QueueEntry> entries = storage.peek(2);
    assertThat(entries).extracting(QueueEntry::json).containsExactly("{\"n\":1}", "{\"n\":2}");
    assertThat(storage.size()).isEqualTo(3);
    storage.remove(entries);
    assertThat(storage.peek(10)).extracting(QueueEntry::json).containsExactly("{\"n\":3}");
    assertThat(storage.size()).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void tracksAttempts(Kind kind, @TempDir Path dir) {
    QueueStorage storage = kind.create(dir, 5);
    storage.enqueue("{}");
    storage.incrementAttempts(storage.peek(1));
    storage.incrementAttempts(storage.peek(1));
    assertThat(storage.peek(1).get(0).attempts()).isEqualTo(2);
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void recordsWhenEventsWereQueued(Kind kind, @TempDir Path dir) {
    TestClock clock = new TestClock();
    QueueStorage storage = kind.create(dir, 5, clock);
    storage.enqueue("{\"n\":1}");
    clock.advance(Duration.ofMinutes(1));
    storage.enqueue("{\"n\":2}");
    assertThat(storage.peek(2))
        .extracting(QueueEntry::enqueuedAt)
        .containsExactly(TestClock.START, TestClock.START.plus(Duration.ofMinutes(1)));
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void dropsOldestWhenFull(Kind kind, @TempDir Path dir) {
    QueueStorage storage = kind.create(dir, 5);
    for (int i = 0; i < 8; i++) {
      storage.enqueue("{\"n\":" + i + "}");
    }
    assertThat(storage.size()).isEqualTo(5);
    assertThat(storage.peek(1).get(0).json()).isEqualTo("{\"n\":3}");
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void clears(Kind kind, @TempDir Path dir) {
    QueueStorage storage = kind.create(dir, 5);
    storage.enqueue("{}");
    storage.clear();
    assertThat(storage.size()).isZero();
    assertThat(storage.peek(10)).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void rejectsInvalidMaxItems(Kind kind, @TempDir Path dir) {
    assertThatThrownBy(() -> kind.create(dir, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxItems");
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void isThreadSafe(Kind kind, @TempDir Path dir) throws Exception {
    QueueStorage storage = kind.create(dir, 10_000);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    int perThread = kind == Kind.MEMORY ? 500 : 50;
    List<Future<?>> futures = new ArrayList<>();
    for (int t = 0; t < 8; t++) {
      futures.add(
          pool.submit(
              () -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                  storage.enqueue("{}");
                  if (i % 10 == 0) {
                    storage.remove(storage.peek(1));
                  }
                }
                return null;
              }));
    }
    start.countDown();
    for (Future<?> future : futures) {
      future.get(30, TimeUnit.SECONDS);
    }
    pool.shutdown();
    // Concurrent peek+remove may remove the same entry twice, so only the bounds are exact.
    assertThat(storage.size()).isBetween(8 * perThread - 8 * (perThread / 10), 8 * perThread);
  }

  @Test
  void fileStorageSurvivesRestart(@TempDir Path dir) {
    TestClock clock = new TestClock();
    FileQueueStorage first = new FileQueueStorage(dir, 10, clock);
    first.enqueue("{\"a\":1}");
    clock.advance(Duration.ofSeconds(1));
    first.enqueue("{\"a\":2}");
    first.incrementAttempts(first.peek(1));

    FileQueueStorage second = new FileQueueStorage(dir, 10, clock);
    List<QueueEntry> entries = second.peek(10);
    assertThat(entries).extracting(QueueEntry::json).containsExactly("{\"a\":1}", "{\"a\":2}");
    assertThat(entries).extracting(QueueEntry::attempts).containsExactly(1, 0);
    assertThat(entries.get(0).enqueuedAt()).isEqualTo(TestClock.START);
  }

  @Test
  void fileStorageIndexStaysConsistentAcrossReopen(@TempDir Path dir) throws Exception {
    TestClock clock = new TestClock();
    FileQueueStorage first = new FileQueueStorage(dir, 10, clock);
    for (int i = 0; i < 4; i++) {
      first.enqueue("{\"n\":" + i + "}"); // same millisecond: ordered by sequence
    }
    List<QueueEntry> queued = first.peek(4);
    first.remove(List.of(queued.get(1)));
    first.incrementAttempts(List.of(queued.get(2)));
    first.incrementAttempts(first.peek(4).subList(1, 2)); // the same event, with its new id

    FileQueueStorage second = new FileQueueStorage(dir, 10, clock);
    assertThat(second.size()).isEqualTo(3);
    // New files must neither replace queued ones nor sort before them.
    second.enqueue("{\"n\":4}");
    second.enqueue("{\"n\":5}");
    List<QueueEntry> entries = second.peek(10);
    assertThat(entries)
        .extracting(QueueEntry::json)
        .containsExactly("{\"n\":0}", "{\"n\":2}", "{\"n\":3}", "{\"n\":4}", "{\"n\":5}");
    assertThat(entries).extracting(QueueEntry::attempts).containsExactly(0, 2, 0, 0, 0);
    try (Stream<Path> files = Files.list(dir)) {
      assertThat(files).hasSize(5);
    }
  }

  @Test
  void fileStorageForgetsFilesDeletedOutsideIt(@TempDir Path dir) throws Exception {
    FileQueueStorage storage = new FileQueueStorage(dir, 10);
    storage.enqueue("{\"n\":1}");
    storage.enqueue("{\"n\":2}");
    Files.delete(dir.resolve(storage.peek(1).get(0).id()));
    assertThat(storage.peek(10)).extracting(QueueEntry::json).containsExactly("{\"n\":2}");
    assertThat(storage.size()).isEqualTo(1);
  }

  @Test
  void fileStorageKeepsTheHighestAttemptCountOfDuplicateFiles(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("0000000001000-000001.0.json"), "{\"old\":true}");
    Files.writeString(dir.resolve("0000000001000-000001.2.json"), "{\"old\":false}");
    FileQueueStorage storage = new FileQueueStorage(dir);
    assertThat(storage.peek(10))
        .singleElement()
        .satisfies(e -> assertThat(e.attempts()).isEqualTo(2));
    assertThat(dir.resolve("0000000001000-000001.0.json")).doesNotExist();
  }

  @Test
  void fileStorageIgnoresForeignAndTempFiles(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("notes.txt"), "x");
    Files.writeString(dir.resolve("123-1.0.json.tmp"), "partial");
    Files.writeString(dir.resolve("bogus.json"), "{}");
    FileQueueStorage storage = new FileQueueStorage(dir);
    storage.enqueue("{\"ok\":true}");
    assertThat(storage.peek(10)).extracting(QueueEntry::json).containsExactly("{\"ok\":true}");
    storage.clear();
    assertThat(dir.resolve("bogus.json")).exists();
  }

  @Test
  void fileStorageReportsDirectoryProblemsAsUncheckedIoExceptions(@TempDir Path dir)
      throws Exception {
    Path file = Files.writeString(dir.resolve("not-a-directory"), "x");
    assertThatThrownBy(() -> new FileQueueStorage(file.resolve("queue")))
        .isInstanceOf(UncheckedIOException.class);
    assertThatThrownBy(() -> new FileKeyValueStorage(file.resolve("state")))
        .isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void keyValueStorage(@TempDir Path dir) {
    FileKeyValueStorage storage = new FileKeyValueStorage(dir);
    assertThat(storage.read("install-id")).isNull();
    storage.write("install-id", "abc");
    assertThat(new FileKeyValueStorage(dir).read("install-id")).isEqualTo("abc");
    assertThatThrownBy(() -> storage.write("../escape", "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
