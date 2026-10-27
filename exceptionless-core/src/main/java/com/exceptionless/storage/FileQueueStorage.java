package com.exceptionless.storage;

import com.exceptionless.internal.Log;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Persists queued events as files so they survive restarts. Each event is one file named {@code
 * <enqueued millis>-<sequence>.<attempts>.json}. A directory should only be used by one process at
 * a time.
 *
 * <p>The directory is read once, when the storage is created; after that an in-memory index of the
 * queued files is kept, so queueing an event writes one file without listing the directory.
 */
public final class FileQueueStorage implements QueueStorage {
  public static final int DEFAULT_MAX_ITEMS = 10_000;
  private static final Log LOG = Log.get(FileQueueStorage.class);
  private static final Pattern FILE_NAME = Pattern.compile("^(\\d+)-(\\d+)\\.(\\d+)\\.json$");
  private static final int SEQUENCE_LIMIT = 1_000_000;

  private final Path directory;
  private final int maxItems;
  private final InstantSource clock;
  // Guarded by this: the queued files, oldest first.
  private final NavigableMap<Key, Stored> index = new TreeMap<>();
  private long sequence;

  /** Orders files by when they were queued, then by their sequence number. */
  private record Key(long millis, long sequence) implements Comparable<Key> {
    private static final Comparator<Key> ORDER =
        Comparator.comparingLong(Key::millis).thenComparingLong(Key::sequence);

    @Override
    public int compareTo(Key other) {
      return ORDER.compare(this, other);
    }
  }

  /**
   * A queued file.
   *
   * @param baseName the file name without the attempts and extension ({@code <millis>-<sequence>})
   */
  private record Stored(String baseName, int attempts) {
    String fileName() {
      return baseName + "." + attempts + ".json";
    }
  }

  /** A parsed file name. */
  private record Parsed(Key key, Stored stored) {
    static @Nullable Parsed of(String fileName) {
      Matcher matcher = FILE_NAME.matcher(fileName);
      if (!matcher.matches()) {
        return null;
      }
      try {
        Key key = new Key(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)));
        String baseName = matcher.group(1) + "-" + matcher.group(2);
        return new Parsed(key, new Stored(baseName, Integer.parseInt(matcher.group(3))));
      } catch (NumberFormatException e) {
        return null; // digits out of range
      }
    }
  }

  public FileQueueStorage(Path directory) {
    this(directory, DEFAULT_MAX_ITEMS);
  }

  public FileQueueStorage(Path directory, int maxItems) {
    this(directory, maxItems, InstantSource.system());
  }

  /**
   * Creates a storage in {@code directory} that drops the oldest events beyond {@code maxItems}.
   *
   * @param clock records when each event was queued
   * @throws java.io.UncheckedIOException when the directory can't be created
   */
  public FileQueueStorage(Path directory, int maxItems, InstantSource clock) {
    if (maxItems < 1) {
      throw new IllegalArgumentException("maxItems must be at least 1");
    }
    this.directory = directory;
    this.maxItems = maxItems;
    this.clock = clock;
    try {
      Files.createDirectories(directory);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to create queue directory " + directory, e);
    }
    loadIndex();
  }

  public Path getDirectory() {
    return directory;
  }

  private void loadIndex() {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
      for (Path file : stream) {
        Parsed parsed = Parsed.of(file.getFileName().toString());
        if (parsed == null) {
          continue; // not a queued event
        }
        // Continue the sequence, so events queued in the same millisecond keep their order.
        sequence = Math.max(sequence, parsed.key().sequence() % SEQUENCE_LIMIT);
        Stored existing = index.get(parsed.key());
        if (existing == null) {
          index.put(parsed.key(), parsed.stored());
        } else if (existing.attempts() < parsed.stored().attempts()) {
          // The same event with two attempt counts: keep the higher count.
          deleteQuietly(directory.resolve(existing.fileName()));
          index.put(parsed.key(), parsed.stored());
        } else {
          deleteQuietly(file);
        }
      }
    } catch (IOException e) {
      LOG.warn("Unable to list queued events in " + directory, e);
    }
  }

  @Override
  public synchronized void enqueue(String json) {
    int excess = index.size() - maxItems + 1;
    for (int i = 0; i < excess; i++) {
      Map.Entry<Key, Stored> oldest = index.pollFirstEntry();
      deleteQuietly(directory.resolve(oldest.getValue().fileName()));
    }
    if (excess > 0) {
      LOG.warn("Persisted event queue is full; dropped " + excess + " of the oldest events");
    }
    Key key = nextKey(clock.millis());
    Stored stored =
        new Stored(String.format(Locale.ROOT, "%013d-%06d", key.millis(), key.sequence()), 0);
    Path target = directory.resolve(stored.fileName());
    Path temp = directory.resolve(stored.fileName() + ".tmp");
    try {
      Files.writeString(temp, json, StandardCharsets.UTF_8);
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      index.put(key, stored);
    } catch (IOException e) {
      deleteQuietly(temp);
      LOG.error("Unable to persist event to " + target, e);
    }
  }

  /** A key that isn't in use, so a new file never replaces a queued one. */
  private Key nextKey(long millis) {
    Key key;
    do {
      sequence = (sequence + 1) % SEQUENCE_LIMIT;
      key = new Key(millis, sequence);
    } while (index.containsKey(key));
    return key;
  }

  @Override
  public synchronized List<QueueEntry> peek(int max) {
    List<QueueEntry> result = new ArrayList<>(Math.min(max, index.size()));
    Iterator<Map.Entry<Key, Stored>> it = index.entrySet().iterator();
    while (result.size() < max && it.hasNext()) {
      Map.Entry<Key, Stored> item = it.next();
      Stored stored = item.getValue();
      Path file = directory.resolve(stored.fileName());
      try {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        result.add(
            new QueueEntry(
                stored.fileName(),
                json,
                stored.attempts(),
                Instant.ofEpochMilli(item.getKey().millis())));
      } catch (NoSuchFileException e) {
        it.remove(); // deleted outside of this storage
      } catch (IOException | RuntimeException e) {
        LOG.warn("Discarding unreadable queued event " + file, e);
        it.remove();
        deleteQuietly(file);
      }
    }
    return result;
  }

  @Override
  public synchronized void remove(Collection<QueueEntry> entries) {
    for (QueueEntry entry : entries) {
      Parsed parsed = Parsed.of(entry.id());
      if (parsed == null) {
        continue;
      }
      Stored stored = index.remove(parsed.key());
      if (stored != null) {
        deleteQuietly(directory.resolve(stored.fileName()));
      }
    }
  }

  @Override
  public synchronized void incrementAttempts(Collection<QueueEntry> entries) {
    for (QueueEntry entry : entries) {
      Parsed parsed = Parsed.of(entry.id());
      if (parsed == null) {
        continue;
      }
      Stored stored = index.get(parsed.key());
      if (stored == null) {
        continue;
      }
      Stored updated = new Stored(stored.baseName(), stored.attempts() + 1);
      try {
        Files.move(
            directory.resolve(stored.fileName()),
            directory.resolve(updated.fileName()),
            StandardCopyOption.ATOMIC_MOVE);
        index.put(parsed.key(), updated);
      } catch (NoSuchFileException e) {
        index.remove(parsed.key());
      } catch (IOException e) {
        LOG.debug(() -> "Unable to update attempts for " + entry.id() + ": " + e);
      }
    }
  }

  @Override
  public synchronized void clear() {
    for (Stored stored : index.values()) {
      deleteQuietly(directory.resolve(stored.fileName()));
    }
    index.clear();
  }

  @Override
  public synchronized int size() {
    return index.size();
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      LOG.debug(() -> "Unable to delete " + file + ": " + e);
    }
  }
}
