package com.exceptionless.storage;

import com.exceptionless.internal.Log;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persists queued events as files so they survive restarts. Each event is one file named {@code
 * <enqueued millis>-<sequence>.<attempts>.json}. A directory should only be used by one process at
 * a time.
 */
public final class FileQueueStorage implements QueueStorage {
  public static final int DEFAULT_MAX_ITEMS = 10_000;
  private static final Log LOG = Log.get(FileQueueStorage.class);
  private static final Pattern FILE_NAME = Pattern.compile("^(\\d+)-(\\d+)\\.(\\d+)\\.json$");

  private final Path directory;
  private final int maxItems;
  private final AtomicLong sequence = new AtomicLong();

  public FileQueueStorage(Path directory) {
    this(directory, DEFAULT_MAX_ITEMS);
  }

  public FileQueueStorage(Path directory, int maxItems) {
    this.directory = directory;
    this.maxItems = maxItems;
    try {
      Files.createDirectories(directory);
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to create queue directory " + directory, e);
    }
  }

  public Path getDirectory() {
    return directory;
  }

  @Override
  public synchronized void enqueue(String json) {
    List<Path> files = listFiles();
    int excess = files.size() - maxItems + 1;
    for (int i = 0; i < excess; i++) {
      deleteQuietly(files.get(i));
    }
    if (excess > 0) {
      LOG.warn("Persisted event queue is full; dropped " + excess + " of the oldest events");
    }
    String name =
        String.format(
            "%013d-%06d.0.json",
            System.currentTimeMillis(), sequence.incrementAndGet() % 1_000_000);
    Path target = directory.resolve(name);
    Path temp = directory.resolve(name + ".tmp");
    try {
      Files.writeString(temp, json, StandardCharsets.UTF_8);
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      deleteQuietly(temp);
      LOG.error("Unable to persist event to " + target, e);
    }
  }

  @Override
  public synchronized List<QueueEntry> peek(int max) {
    List<QueueEntry> result = new ArrayList<>();
    for (Path file : listFiles()) {
      if (result.size() >= max) {
        break;
      }
      Matcher matcher = FILE_NAME.matcher(file.getFileName().toString());
      if (!matcher.matches()) {
        continue;
      }
      try {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        result.add(
            new QueueEntry(
                file.getFileName().toString(),
                json,
                Integer.parseInt(matcher.group(3)),
                Instant.ofEpochMilli(Long.parseLong(matcher.group(1)))));
      } catch (NoSuchFileException e) {
        // Removed concurrently.
      } catch (IOException | RuntimeException e) {
        LOG.warn("Discarding unreadable queued event " + file, e);
        deleteQuietly(file);
      }
    }
    return result;
  }

  @Override
  public synchronized void remove(Collection<QueueEntry> entries) {
    for (QueueEntry entry : entries) {
      deleteQuietly(directory.resolve(entry.id()));
    }
  }

  @Override
  public synchronized void incrementAttempts(Collection<QueueEntry> entries) {
    for (QueueEntry entry : entries) {
      Matcher matcher = FILE_NAME.matcher(entry.id());
      if (!matcher.matches()) {
        continue;
      }
      String renamed =
          matcher.group(1) + "-" + matcher.group(2) + "." + (entry.attempts() + 1) + ".json";
      try {
        Files.move(
            directory.resolve(entry.id()),
            directory.resolve(renamed),
            StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        LOG.debug(() -> "Unable to update attempts for " + entry.id() + ": " + e);
      }
    }
  }

  @Override
  public synchronized void clear() {
    for (Path file : listFiles()) {
      deleteQuietly(file);
    }
  }

  @Override
  public synchronized int size() {
    return listFiles().size();
  }

  private List<Path> listFiles() {
    List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
      for (Path file : stream) {
        files.add(file);
      }
    } catch (IOException e) {
      LOG.warn("Unable to list queued events in " + directory, e);
    }
    files.sort(null);
    return files;
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      LOG.debug(() -> "Unable to delete " + file + ": " + e);
    }
  }
}
