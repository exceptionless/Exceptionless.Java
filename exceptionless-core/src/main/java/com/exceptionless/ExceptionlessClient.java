package com.exceptionless;

import com.exceptionless.internal.DaemonThreadFactory;
import com.exceptionless.internal.Ids;
import com.exceptionless.internal.Log;
import com.exceptionless.internal.SettingsManager;
import com.exceptionless.internal.Strings;
import com.exceptionless.models.Event;
import com.exceptionless.models.EventTypes;
import com.exceptionless.models.KnownDataKeys;
import com.exceptionless.models.UserDescription;
import com.exceptionless.models.UserInfo;
import com.exceptionless.plugins.ContextData;
import com.exceptionless.plugins.EventPlugin;
import com.exceptionless.plugins.EventPluginContext;
import com.exceptionless.plugins.HeartbeatPlugin;
import com.exceptionless.queue.DefaultEventQueue;
import com.exceptionless.queue.EventQueue;
import com.exceptionless.storage.FileKeyValueStorage;
import com.exceptionless.storage.FileQueueStorage;
import com.exceptionless.storage.InMemoryKeyValueStorage;
import com.exceptionless.storage.InMemoryQueueStorage;
import com.exceptionless.storage.KeyValueStorage;
import com.exceptionless.storage.QueueStorage;
import com.exceptionless.submission.DefaultSubmissionClient;
import com.exceptionless.submission.SubmissionClient;
import com.exceptionless.submission.SubmissionResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Sends events to Exceptionless.
 *
 * <pre>{@code
 * try (ExceptionlessClient client = ExceptionlessClient.create(c -> c.apiKey("YOUR_API_KEY"))) {
 *   client.submitLog("App started");
 *   try {
 *     doWork();
 *   } catch (Exception e) {
 *     client.createException(e).addTags("work").submit();
 *   }
 * }
 * }</pre>
 *
 * <p>Submitting never blocks on the network: events run through the plugins on the calling thread,
 * are queued, and are sent in batches by a single background daemon thread. Closing the client (or
 * JVM shutdown) sends whatever is still queued.
 *
 * <p>The client starts on first use, or explicitly with {@link #startup()}. All methods are
 * thread-safe.
 */
public final class ExceptionlessClient implements AutoCloseable {
  private static final Log LOG = Log.get(ExceptionlessClient.class);
  private static final ThreadLocal<Boolean> SUBMITTING = new ThreadLocal<>();

  private enum State {
    NEW,
    STARTED,
    CLOSED
  }

  private final ExceptionlessConfiguration config;
  private final Clock clock;
  private final Object lifecycleLock = new Object();
  private volatile State state = State.NEW;

  private volatile @Nullable ScheduledThreadPoolExecutor scheduler;
  private volatile @Nullable EventQueue queue;
  private volatile @Nullable SettingsManager settingsManager;
  private volatile @Nullable SubmissionClient submissionClient;
  private volatile @Nullable String installId;
  private volatile @Nullable String lastReferenceId;

  private final Set<EventPlugin> startedPlugins =
      Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
  private final List<Consumer<SubmittingEvent>> submittingHandlers = new CopyOnWriteArrayList<>();
  private final List<Consumer<Event>> submittedHandlers = new CopyOnWriteArrayList<>();

  private @Nullable Thread shutdownHook;
  private volatile boolean shuttingDownFromHook;
  private Thread.@Nullable UncaughtExceptionHandler installedHandler;
  private Thread.@Nullable UncaughtExceptionHandler previousHandler;

  /** Creates a client. It starts automatically when first used. */
  public ExceptionlessClient(ExceptionlessConfiguration configuration) {
    // Event dates carry the local UTC offset, like the other Exceptionless clients.
    this(configuration, Clock.system(ZoneId.systemDefault()));
  }

  /** For tests: {@code clock} dates new events, in its time zone. */
  ExceptionlessClient(ExceptionlessConfiguration configuration, Clock clock) {
    this.config = configuration;
    this.clock = clock;
  }

  /**
   * Creates and starts a client configured from the environment (see {@link
   * ExceptionlessConfiguration#fromEnvironment()}) and then {@code configure}.
   */
  public static ExceptionlessClient create(Consumer<ExceptionlessConfiguration> configure) {
    ExceptionlessConfiguration config = ExceptionlessConfiguration.fromEnvironment();
    configure.accept(config);
    return new ExceptionlessClient(config).startup();
  }

  /** Creates and starts a client for the project with the given API key. */
  public static ExceptionlessClient create(String apiKey) {
    return create(c -> c.apiKey(apiKey));
  }

  public ExceptionlessConfiguration getConfiguration() {
    return config;
  }

  // ---------------------------------------------------------------------------------------------
  // Lifecycle

  /**
   * Starts background processing: sending queued events, refreshing project settings, the uncaught
   * exception handler and shutdown hook (when enabled) and sessions (when enabled). Safe to call
   * more than once.
   */
  public ExceptionlessClient startup() {
    boolean startSession;
    synchronized (lifecycleLock) {
      if (state != State.NEW) {
        return this;
      }
      config.lock();
      initialize();
      state = State.STARTED;
      startSession = config.isEnabled() && config.isSessionsEnabled();
    }
    if (startSession) {
      submitSessionStart();
    }
    return this;
  }

  private void initialize() {
    List<String> problems = config.validate();
    if (!problems.isEmpty()) {
      LOG.warn("Exceptionless is disabled: " + String.join("; ", problems));
    }

    QueueStorage queueStorage;
    KeyValueStorage stateStorage;
    Path storagePath = config.getStoragePath();
    QueueStorage fileQueue = null;
    KeyValueStorage fileState = null;
    if (storagePath != null) {
      Path directory = storagePath.resolve(storageFolder(config.getApiKey()));
      try {
        fileQueue = new FileQueueStorage(directory.resolve("queue"), config.getMaxQueueSize());
        fileState = new FileKeyValueStorage(directory);
      } catch (RuntimeException e) {
        LOG.error("Unable to use storage path " + directory + "; keeping events in memory", e);
      }
    }
    if (fileQueue != null && fileState != null) {
      queueStorage = fileQueue;
      stateStorage = fileState;
    } else {
      queueStorage = new InMemoryQueueStorage(config.getMaxQueueSize());
      stateStorage = new InMemoryKeyValueStorage();
    }

    String id = stateStorage.read("install-id");
    if (id == null || id.isBlank()) {
      id = Ids.newGuid();
      stateStorage.write("install-id", id);
    }
    installId = id.strip();

    SubmissionClient client = config.getSubmissionClient();
    if (client == null) {
      client = new DefaultSubmissionClient(config);
    }
    submissionClient = client;

    SettingsManager settings = new SettingsManager(config, client, stateStorage);
    boolean loadedSettings = settings.loadSavedSettings();
    settingsManager = settings;

    EventQueue eventQueue = new DefaultEventQueue(config, client, queueStorage);
    eventQueue.onEventsPosted((count, response) -> settings.checkVersion(response.configVersion()));
    queue = eventQueue;

    ScheduledThreadPoolExecutor executor =
        new ScheduledThreadPoolExecutor(
            1, new DaemonThreadFactory(DaemonThreadFactory.CLIENT_THREAD_NAME));
    executor.setRemoveOnCancelPolicy(true);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    scheduler = executor;

    if (!config.isEnabled()) {
      return;
    }

    Duration interval = config.getQueueProcessInterval();
    Duration initialDelay =
        interval.compareTo(Duration.ofSeconds(2)) < 0 ? interval : Duration.ofSeconds(2);
    scheduleWithFixedDelay(eventQueue::process, initialDelay, interval);

    Duration settingsDelay = Duration.ofSeconds(loadedSettings ? 15 : 5);
    Duration settingsInterval = config.getUpdateSettingsWhenIdleInterval();
    if (settingsInterval.isZero()) {
      schedule(settings::updateSettings, settingsDelay);
    } else {
      scheduleWithFixedDelay(settings::updateSettings, settingsDelay, settingsInterval);
    }

    if (config.isCaptureUncaughtExceptions()) {
      installUncaughtExceptionHandler();
    }
    if (config.isRegisterShutdownHook()) {
      Thread hook =
          new Thread(
              () -> {
                shuttingDownFromHook = true;
                close();
              },
              DaemonThreadFactory.CLIENT_THREAD_NAME + "-shutdown");
      try {
        Runtime.getRuntime().addShutdownHook(hook);
        shutdownHook = hook;
      } catch (IllegalStateException e) {
        // The JVM is already shutting down.
      }
    }
  }

  static String storageFolder(@Nullable String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      return "default";
    }
    String prefix = apiKey.length() > 8 ? apiKey.substring(0, 8) : apiKey;
    return prefix.replaceAll("[^A-Za-z0-9_-]", "_");
  }

  private void installUncaughtExceptionHandler() {
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.UncaughtExceptionHandler handler =
        (thread, error) -> {
          try {
            createUnhandledException(error, "uncaughtExceptionHandler")
                .setProperty("thread", thread.getName())
                .submit();
          } catch (Throwable ignored) {
            // Never let reporting prevent the normal handling.
          }
          if (previous != null) {
            previous.uncaughtException(thread, error);
          } else {
            System.err.print("Exception in thread \"" + thread.getName() + "\" ");
            error.printStackTrace(System.err);
          }
        };
    Thread.setDefaultUncaughtExceptionHandler(handler);
    installedHandler = handler;
    previousHandler = previous;
  }

  /**
   * Whether the default handler is still the one this client installed. Identity is what matters:
   * if the application replaced it since, the replacement must be left alone.
   */
  @SuppressWarnings("ReferenceEquality")
  private boolean isOurUncaughtExceptionHandlerInstalled() {
    return installedHandler != null
        && Thread.getDefaultUncaughtExceptionHandler() == installedHandler;
  }

  /** {@return whether the client has started and is not yet closed} */
  public boolean isStarted() {
    return state == State.STARTED;
  }

  /**
   * Ends the session (when sessions are enabled), sends queued events (waiting up to the configured
   * {@link ExceptionlessConfiguration#getShutdownTimeout() shutdown timeout}) and stops the
   * background thread. Events submitted afterwards are discarded.
   */
  @Override
  public void close() {
    synchronized (lifecycleLock) {
      if (state != State.STARTED) {
        state = State.CLOSED;
        return;
      }
      state = State.CLOSED;
    }
    long deadline = System.nanoTime() + config.getShutdownTimeout().toNanos();

    Thread hook = shutdownHook;
    if (hook != null && !shuttingDownFromHook) {
      try {
        Runtime.getRuntime().removeShutdownHook(hook);
      } catch (IllegalStateException e) {
        // Already shutting down.
      }
    }
    if (isOurUncaughtExceptionHandlerInstalled()) {
      Thread.setDefaultUncaughtExceptionHandler(previousHandler);
    }

    ScheduledThreadPoolExecutor executor = scheduler;
    if (executor == null) {
      return;
    }
    if (config.isEnabled()) {
      if (config.isSessionsEnabled()) {
        String identifier = sessionIdentifier(null);
        if (identifier != null) {
          await(runAsync(() -> sendHeartbeat(identifier, true)), deadline);
        }
      }
      for (EventPlugin plugin : config.getPlugins()) {
        if (startedPlugins.contains(plugin)) {
          try {
            plugin.suspend(this);
          } catch (RuntimeException e) {
            LOG.warn("Plugin " + plugin.getName() + " failed to suspend", e);
          }
        }
      }
      EventQueue eventQueue = queue;
      if (eventQueue != null) {
        await(runAsync(eventQueue::process), deadline);
      }
    }
    executor.shutdownNow();
  }

  private static void await(Future<?> future, long deadlineNanos) {
    long remaining = deadlineNanos - System.nanoTime();
    try {
      future.get(Math.max(0, remaining), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      LOG.warn("Timed out sending queued events during shutdown");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      LOG.warn("Error during shutdown", e.getCause());
    }
  }

  private boolean ensureStarted() {
    State current = state;
    if (current == State.STARTED) {
      return true;
    }
    if (current == State.NEW) {
      startup();
      return state == State.STARTED;
    }
    return false;
  }

  // ---------------------------------------------------------------------------------------------
  // Creating events

  /** Creates an event of any type. */
  public EventBuilder createEvent() {
    Event event = new Event();
    event.setDate(OffsetDateTime.now(clock));
    return new EventBuilder(this, event, new ContextData());
  }

  /** Creates an error event for an exception. */
  public EventBuilder createException(Throwable exception) {
    return createEvent().setException(exception);
  }

  /**
   * Creates an error event for an exception that crashed a thread or request.
   *
   * @param submissionMethod how it was captured, e.g. {@code servletFilter}
   */
  public EventBuilder createUnhandledException(
      Throwable exception, @Nullable String submissionMethod) {
    return createException(exception).markAsUnhandledError(submissionMethod);
  }

  public EventBuilder createLog(String message) {
    return createEvent().setType(EventTypes.LOG).setMessage(message);
  }

  /** Creates a log event. The source is usually the logger name. */
  public EventBuilder createLog(@Nullable String source, String message) {
    return createLog(message).setSource(source);
  }

  public EventBuilder createLog(@Nullable String source, String message, @Nullable LogLevel level) {
    return createLog(source, message).setLevel(level);
  }

  /** Creates a feature usage event, for tracking how often a feature is used. */
  public EventBuilder createFeatureUsage(String feature) {
    return createEvent().setType(EventTypes.FEATURE_USAGE).setSource(feature);
  }

  /** Creates a 404 (not found) event for a resource or path. */
  public EventBuilder createNotFound(String resource) {
    return createEvent().setType(EventTypes.NOT_FOUND).setSource(resource);
  }

  /** Creates a session start event. Sessions are tied to the user identity or a session id. */
  public EventBuilder createSessionStart() {
    return createEvent().setType(EventTypes.SESSION);
  }

  // ---------------------------------------------------------------------------------------------
  // Submitting events

  public void submitException(Throwable exception) {
    createException(exception).submit();
  }

  public void submitUnhandledException(Throwable exception, @Nullable String submissionMethod) {
    createUnhandledException(exception, submissionMethod).submit();
  }

  public void submitLog(String message) {
    createLog(message).submit();
  }

  public void submitLog(@Nullable String source, String message) {
    createLog(source, message).submit();
  }

  public void submitLog(@Nullable String source, String message, @Nullable LogLevel level) {
    createLog(source, message, level).submit();
  }

  public void submitFeatureUsage(String feature) {
    createFeatureUsage(feature).submit();
  }

  public void submitNotFound(String resource) {
    createNotFound(resource).submit();
  }

  public void submitSessionStart() {
    createSessionStart().submit();
  }

  /**
   * Runs an event through the plugins and queues it.
   *
   * @return the plugin context; {@link EventPluginContext#isCancelled()} tells whether the event
   *     was discarded
   */
  public EventPluginContext submitEvent(Event event, @Nullable ContextData contextData) {
    ContextData data = contextData != null ? contextData : new ContextData();
    EventPluginContext context = new EventPluginContext(this, event, data);
    if (!ensureStarted() || !config.isEnabled()) {
      context.cancel();
      return context;
    }
    if (SUBMITTING.get() != null) {
      // Reentrant submission (e.g. a plugin logging through an Exceptionless appender).
      context.cancel();
      return context;
    }
    SUBMITTING.set(true);
    try {
      if (event.getDate() == null) {
        event.setDate(OffsetDateTime.now(clock));
      }
      runPlugins(context);
      if (context.isCancelled()) {
        return context;
      }
      if (event.getType() == null) {
        event.setType(EventTypes.LOG);
      }
      SubmittingEvent submitting = new SubmittingEvent(this, event, data);
      for (Consumer<SubmittingEvent> handler : submittingHandlers) {
        try {
          handler.accept(submitting);
        } catch (RuntimeException e) {
          LOG.warn("SubmittingEvent handler failed", e);
        }
      }
      if (submitting.isCancelled()) {
        context.cancel();
        return context;
      }
      EventQueue eventQueue = queue;
      if (eventQueue == null) {
        context.cancel();
        return context;
      }
      eventQueue.enqueue(event);
      if (event.getReferenceId() != null) {
        lastReferenceId = event.getReferenceId();
      }
      for (Consumer<Event> handler : submittedHandlers) {
        try {
          handler.accept(event);
        } catch (RuntimeException e) {
          LOG.warn("SubmittedEvent handler failed", e);
        }
      }
      return context;
    } finally {
      SUBMITTING.remove();
    }
  }

  private void runPlugins(EventPluginContext context) {
    for (EventPlugin plugin : config.getPlugins()) {
      if (context.isCancelled()) {
        return;
      }
      if (!startedPlugins.contains(plugin)) {
        synchronized (startedPlugins) {
          if (startedPlugins.add(plugin)) {
            try {
              plugin.startup(this);
            } catch (RuntimeException e) {
              LOG.warn("Plugin " + plugin.getName() + " failed to start", e);
            }
          }
        }
      }
      try {
        plugin.run(context);
      } catch (RuntimeException | LinkageError e) {
        LOG.error("Plugin " + plugin.getName() + " failed; continuing with the next plugin", e);
      }
    }
  }

  /**
   * Adds a handler called after the plugins run and before the event is queued. Handlers can change
   * the event or {@link SubmittingEvent#cancel() cancel} it.
   */
  public ExceptionlessClient addSubmittingEventHandler(Consumer<SubmittingEvent> handler) {
    submittingHandlers.add(handler);
    return this;
  }

  /** Adds a handler called after an event is queued. */
  public ExceptionlessClient addSubmittedEventHandler(Consumer<Event> handler) {
    submittedHandlers.add(handler);
    return this;
  }

  /** {@return the reference id of the last error submitted, to show to users} */
  public @Nullable String getLastReferenceId() {
    return lastReferenceId;
  }

  /**
   * Whether a log message from {@code source} at {@code level} would be sent according to the
   * project's {@code @@log} settings, using {@code defaultMinimum} when no setting matches. Logging
   * integrations call this before building events.
   */
  public boolean isLogLevelEnabled(
      @Nullable String source, LogLevel level, @Nullable LogLevel defaultMinimum) {
    return config.isLogLevelEnabled(source, level, defaultMinimum);
  }

  // ---------------------------------------------------------------------------------------------
  // Sessions and user descriptions

  /**
   * Sends a session heartbeat for the given session id or user identity (default: the current
   * session or the configured user).
   */
  public CompletableFuture<SubmissionResponse> submitSessionHeartbeat(
      @Nullable String sessionIdOrUserId) {
    String identifier = sessionIdentifier(sessionIdOrUserId);
    if (identifier == null) {
      return CompletableFuture.completedFuture(
          SubmissionResponse.notSent("No session or user identity"));
    }
    return supplyAsync(() -> sendHeartbeat(identifier, false));
  }

  /** Ends a session (default: the current session or the configured user). */
  public CompletableFuture<SubmissionResponse> submitSessionEnd(
      @Nullable String sessionIdOrUserId) {
    String identifier = sessionIdentifier(sessionIdOrUserId);
    if (identifier == null) {
      return CompletableFuture.completedFuture(
          SubmissionResponse.notSent("No session or user identity"));
    }
    if (identifier.equals(config.getCurrentSessionIdentifier())) {
      config.currentSessionIdentifier(null);
    }
    for (EventPlugin plugin : config.getPlugins()) {
      if (plugin instanceof HeartbeatPlugin heartbeat) {
        heartbeat.stop();
      }
    }
    return supplyAsync(() -> sendHeartbeat(identifier, true));
  }

  private @Nullable String sessionIdentifier(@Nullable String explicit) {
    if (!Strings.isBlank(explicit)) {
      return explicit;
    }
    String current = config.getCurrentSessionIdentifier();
    if (current != null) {
      return current;
    }
    if (config.getDefaultData().get(KnownDataKeys.USER_INFO) instanceof UserInfo user
        && !Strings.isBlank(user.identity())) {
      return user.identity();
    }
    return null;
  }

  private SubmissionResponse sendHeartbeat(String identifier, boolean close) {
    SubmissionClient client = submissionClient;
    if (client == null || !config.isEnabled()) {
      return SubmissionResponse.notSent("Exceptionless is disabled");
    }
    return client.sendHeartbeat(identifier, close);
  }

  /**
   * Attaches a user's email address and description to a submitted event, for example from an error
   * page where the user explains what they were doing.
   */
  public CompletableFuture<SubmissionResponse> updateUserEmailAndDescription(
      String referenceId, @Nullable String emailAddress, @Nullable String description) {
    if (!Ids.isValidReferenceId(referenceId)) {
      throw new IllegalArgumentException("Invalid reference id: " + referenceId);
    }
    UserDescription userDescription = new UserDescription(emailAddress, description);
    return supplyAsync(
        () -> {
          SubmissionClient client = submissionClient;
          if (client == null || !config.isEnabled()) {
            return SubmissionResponse.notSent("Exceptionless is disabled");
          }
          SubmissionResponse response = client.postUserDescription(referenceId, userDescription);
          SettingsManager settings = settingsManager;
          if (settings != null) {
            settings.checkVersion(response.configVersion());
          }
          return response;
        });
  }

  // ---------------------------------------------------------------------------------------------
  // Queue and background work

  /** {@return the event queue} */
  public EventQueue getQueue() {
    ensureStarted();
    EventQueue eventQueue = queue;
    if (eventQueue == null) {
      throw new IllegalStateException("The client has been closed");
    }
    return eventQueue;
  }

  /** Sends queued events now, on the background thread. */
  public CompletableFuture<Void> processQueue() {
    if (!ensureStarted()) {
      return CompletableFuture.completedFuture(null);
    }
    EventQueue eventQueue = queue;
    return eventQueue == null
        ? CompletableFuture.completedFuture(null)
        : runAsync(eventQueue::process);
  }

  /**
   * Sends queued events and waits for them to be sent.
   *
   * @return true when the queue is empty afterwards
   */
  public boolean flush(Duration timeout) {
    CompletableFuture<Void> future = processQueue();
    try {
      future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (ExecutionException e) {
      return false;
    }
    EventQueue eventQueue = queue;
    return eventQueue == null || eventQueue.size() == 0;
  }

  /** Fetches the latest project settings now. */
  public CompletableFuture<Void> updateSettings() {
    if (!ensureStarted()) {
      return CompletableFuture.completedFuture(null);
    }
    SettingsManager settings = settingsManager;
    return settings == null
        ? CompletableFuture.completedFuture(null)
        : runAsync(settings::updateSettings);
  }

  /** {@return a unique id for this installation, persisted when file storage is used} */
  public @Nullable String getInstallId() {
    ensureStarted();
    return installId;
  }

  /**
   * Runs a task periodically on the client's background thread. For plugins. The task stops when
   * the client is closed.
   */
  public ScheduledFuture<?> scheduleAtFixedRate(
      Runnable task, Duration initialDelay, Duration period) {
    ensureStarted();
    ScheduledThreadPoolExecutor executor = requireScheduler();
    return executor.scheduleAtFixedRate(
        safe(task), initialDelay.toMillis(), Math.max(1, period.toMillis()), TimeUnit.MILLISECONDS);
  }

  // The futures of the client's own tasks aren't needed: the tasks end when close() shuts the
  // executor down, and safe() logs their failures.
  @SuppressWarnings("FutureReturnValueIgnored")
  private void scheduleWithFixedDelay(Runnable task, Duration initialDelay, Duration delay) {
    requireScheduler()
        .scheduleWithFixedDelay(
            safe(task),
            initialDelay.toMillis(),
            Math.max(1, delay.toMillis()),
            TimeUnit.MILLISECONDS);
  }

  @SuppressWarnings("FutureReturnValueIgnored") // see scheduleWithFixedDelay
  private void schedule(Runnable task, Duration delay) {
    requireScheduler().schedule(safe(task), delay.toMillis(), TimeUnit.MILLISECONDS);
  }

  private ScheduledThreadPoolExecutor requireScheduler() {
    ScheduledThreadPoolExecutor executor = scheduler;
    if (executor == null) {
      throw new IllegalStateException("The client has not started");
    }
    return executor;
  }

  private CompletableFuture<Void> runAsync(Runnable task) {
    return supplyAsync(
        () -> {
          task.run();
          return null;
        });
  }

  private <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
    ensureStarted();
    ScheduledThreadPoolExecutor executor = scheduler;
    if (executor == null || executor.isShutdown()) {
      return CompletableFuture.failedFuture(new IllegalStateException("The client is not running"));
    }
    try {
      return CompletableFuture.supplyAsync(task, executor);
    } catch (RejectedExecutionException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private static Runnable safe(Runnable task) {
    return () -> {
      try {
        task.run();
      } catch (RuntimeException e) {
        LOG.error("Background task failed", e);
      }
    };
  }
}
