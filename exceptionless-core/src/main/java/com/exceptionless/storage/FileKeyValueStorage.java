package com.exceptionless.storage;

import com.exceptionless.internal.Log;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Persists client state as small files in a directory. */
public final class FileKeyValueStorage implements KeyValueStorage {
  private static final Log LOG = Log.get(FileKeyValueStorage.class);
  private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9._-]+");

  private final Path directory;

  public FileKeyValueStorage(Path directory) {
    this.directory = directory;
    try {
      Files.createDirectories(directory);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to create storage directory " + directory, e);
    }
  }

  @Override
  public @Nullable String read(String key) {
    try {
      return Files.readString(file(key), StandardCharsets.UTF_8);
    } catch (NoSuchFileException e) {
      return null;
    } catch (IOException e) {
      LOG.warn("Unable to read " + key, e);
      return null;
    }
  }

  @Override
  public void write(String key, String value) {
    Path target = file(key);
    Path temp = directory.resolve(key + ".tmp");
    try {
      Files.writeString(temp, value, StandardCharsets.UTF_8);
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      LOG.warn("Unable to write " + key, e);
    }
  }

  private Path file(String key) {
    if (!SAFE_KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("Invalid storage key: " + key);
    }
    return directory.resolve(key);
  }
}
