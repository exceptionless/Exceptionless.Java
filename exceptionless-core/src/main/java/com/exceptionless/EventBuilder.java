package com.exceptionless;

import com.exceptionless.internal.DataNormalizer;
import com.exceptionless.internal.Exclusions;
import com.exceptionless.internal.Ids;
import com.exceptionless.internal.Strings;
import com.exceptionless.json.JsonSerializable;
import com.exceptionless.models.Event;
import com.exceptionless.models.EventTags;
import com.exceptionless.models.ManualStackingInfo;
import com.exceptionless.models.RequestInfo;
import com.exceptionless.models.UserDescription;
import com.exceptionless.models.UserInfo;
import com.exceptionless.plugins.ContextData;
import com.exceptionless.plugins.EventPluginContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Builds an event fluently and submits it:
 *
 * <pre>{@code
 * client.createException(e)
 *     .addTags("checkout")
 *     .setProperty("order", order)
 *     .setUserIdentity(user.getId(), user.getName())
 *     .submit();
 * }</pre>
 *
 * Builders are not thread-safe; create one per event.
 */
public final class EventBuilder {
  private final ExceptionlessClient client;
  private final Event event;
  private final ContextData contextData;

  EventBuilder(ExceptionlessClient client, Event event, ContextData contextData) {
    this.client = client;
    this.event = event;
    this.contextData = contextData;
  }

  /** {@return the event being built} */
  public Event getEvent() {
    return event;
  }

  /** Data available to plugins but not sent to the server. */
  public ContextData getContextData() {
    return contextData;
  }

  /**
   * Overrides the deployment environment for this event, instead of the configured default. The
   * name is trimmed and keeps its casing. A null or invalid name (empty, longer than 64 characters
   * or with control characters) clears the environment: the event is sent without one and does not
   * fall back to the default.
   */
  public EventBuilder setEnvironment(@Nullable String environment) {
    event.setEnvironment(environment);
    contextData.setEnvironmentOverride(true);
    return this;
  }

  public EventBuilder setType(@Nullable String type) {
    if (!Strings.isBlank(type)) {
      event.setType(type);
    }
    return this;
  }

  /** The source: the logger name for logs, the feature for usage, the path for 404s. */
  public EventBuilder setSource(@Nullable String source) {
    if (!Strings.isBlank(source)) {
      event.setSource(source);
    }
    return this;
  }

  public EventBuilder setMessage(@Nullable String message) {
    if (!Strings.isBlank(message)) {
      event.setMessage(message);
    }
    return this;
  }

  /** Sets the log level ({@code @level}). */
  public EventBuilder setLevel(@Nullable LogLevel level) {
    event.setLevel(level == null ? null : level.value());
    return this;
  }

  public EventBuilder setDate(OffsetDateTime date) {
    event.setDate(date);
    return this;
  }

  /**
   * Sets a unique reference id for this occurrence, for example to show users and look the event up
   * later. Must be 8-100 characters of letters, digits and dashes.
   *
   * @throws IllegalArgumentException if the id is invalid
   */
  public EventBuilder setReferenceId(String referenceId) {
    if (!Ids.isValidReferenceId(referenceId)) {
      throw new IllegalArgumentException(
          "Reference id must be 8-100 characters of letters, digits and dashes: " + referenceId);
    }
    event.setReferenceId(referenceId);
    return this;
  }

  /**
   * Links this event to another event by reference id (stored as {@code @ref:<name>}).
   *
   * @throws IllegalArgumentException if the name is blank or the id is invalid
   */
  public EventBuilder setEventReference(String name, String referenceId) {
    if (Strings.isBlank(name)) {
      throw new IllegalArgumentException("A reference name is required");
    }
    if (!Ids.isValidReferenceId(referenceId)) {
      throw new IllegalArgumentException(
          "Reference id must be 8-100 characters of letters, digits and dashes: " + referenceId);
    }
    event.setEventReference(name, referenceId);
    return this;
  }

  /**
   * Sets where the event happened.
   *
   * @throws IllegalArgumentException if the coordinates are out of range
   */
  public EventBuilder setGeo(double latitude, double longitude) {
    if (latitude < -90.0 || latitude > 90.0) {
      throw new IllegalArgumentException("Latitude must be between -90 and 90: " + latitude);
    }
    if (longitude < -180.0 || longitude > 180.0) {
      throw new IllegalArgumentException("Longitude must be between -180 and 180: " + longitude);
    }
    event.setGeo(
        BigDecimal.valueOf(latitude).toPlainString()
            + ","
            + BigDecimal.valueOf(longitude).toPlainString());
    return this;
  }

  /** Sets a numeric value, e.g. a duration or amount, that the server aggregates. */
  public EventBuilder setValue(@Nullable Number value) {
    if (value == null) {
      event.setValue(null);
    } else if (value instanceof BigDecimal decimal) {
      event.setValue(decimal);
    } else if (value instanceof Double || value instanceof Float) {
      double d = value.doubleValue();
      if (!Double.isNaN(d) && !Double.isInfinite(d)) {
        event.setValue(BigDecimal.valueOf(d));
      }
    } else {
      event.setValue(new BigDecimal(value.toString()));
    }
    return this;
  }

  /** Sets how many times this event happened. */
  public EventBuilder setCount(int count) {
    event.setCount(count);
    return this;
  }

  public EventBuilder addTags(String... tags) {
    event.addTags(tags);
    return this;
  }

  public EventBuilder addTags(Collection<String> tags) {
    event.addTags(tags);
    return this;
  }

  /** Marks the event as critical. Critical events can trigger notifications. */
  public EventBuilder markAsCritical() {
    return markAsCritical(true);
  }

  public EventBuilder markAsCritical(boolean critical) {
    if (critical) {
      event.addTags(EventTags.CRITICAL);
    } else {
      event.removeTags(EventTags.CRITICAL);
    }
    return this;
  }

  /** Sets the application version ({@code @version}) for this event. */
  public EventBuilder setVersion(@Nullable String version) {
    event.setVersion(Strings.stripToNull(version));
    return this;
  }

  public EventBuilder setUserIdentity(@Nullable String identity) {
    return setUserIdentity(identity, null);
  }

  /** Sets the user the event happened to. Used for user tracking and sessions. */
  public EventBuilder setUserIdentity(@Nullable String identity, @Nullable String name) {
    if (Strings.isBlank(identity) && Strings.isBlank(name)) {
      return this;
    }
    return setUserIdentity(new UserInfo(identity, name));
  }

  public EventBuilder setUserIdentity(@Nullable UserInfo user) {
    event.setUserInfo(user);
    return this;
  }

  /** Sets what the user said they were doing when the error happened. */
  public EventBuilder setUserDescription(
      @Nullable String emailAddress, @Nullable String description) {
    if (Strings.isBlank(emailAddress) && Strings.isBlank(description)) {
      return this;
    }
    event.setUserDescription(new UserDescription(emailAddress, description));
    return this;
  }

  /** Groups all events with the same key into one stack, instead of the automatic stacking. */
  public EventBuilder setManualStackingKey(String key) {
    return setManualStackingKey(key, null);
  }

  public EventBuilder setManualStackingKey(String key, @Nullable String title) {
    if (!Strings.isBlank(key)) {
      event.setManualStackingInfo(ManualStackingInfo.ofKey(key, title));
    }
    return this;
  }

  /** Groups all events with the same signature data into one stack. */
  public EventBuilder setManualStackingInfo(
      Map<String, String> signatureData, @Nullable String title) {
    if (!signatureData.isEmpty()) {
      event.setManualStackingInfo(new ManualStackingInfo(title, Map.copyOf(signatureData)));
    }
    return this;
  }

  /**
   * Adds a custom property. Objects are converted to JSON using their public getters (up to 5
   * levels deep). Names matching the data exclusions are skipped.
   */
  public EventBuilder setProperty(String name, @Nullable Object value) {
    return setProperty(name, value, DataNormalizer.DEFAULT_MAX_DEPTH);
  }

  /**
   * Adds a custom property.
   *
   * @param maxDepth how many levels of nested objects to include
   * @param excludedPropertyNames extra property names (wildcards supported) to leave out
   */
  public EventBuilder setProperty(
      String name, @Nullable Object value, int maxDepth, String... excludedPropertyNames) {
    if (Strings.isBlank(name) || value == null) {
      return this;
    }
    Exclusions excluded =
        client
            .getConfiguration()
            .compiledDataExclusions()
            .plus(Exclusions.of(List.of(excludedPropertyNames)));
    if (excluded.test(name)) {
      return this;
    }
    Object normalized =
        value instanceof JsonSerializable
            ? value
            : DataNormalizer.normalize(value, maxDepth, excluded);
    if (normalized != null) {
      event.getData().put(name, normalized);
    }
    return this;
  }

  /** Sets the exception for this event. Its type is set to {@code error}. */
  public EventBuilder setException(@Nullable Throwable exception) {
    contextData.setException(exception);
    return this;
  }

  /** Attaches the request the event happened during. Privacy settings are applied later. */
  public EventBuilder setRequestInfo(@Nullable RequestInfo request) {
    contextData.setRequestInfo(request);
    return this;
  }

  /** Records how the event was captured, e.g. {@code servletFilter}. */
  public EventBuilder setSubmissionMethod(@Nullable String submissionMethod) {
    contextData.setSubmissionMethod(submissionMethod);
    return this;
  }

  /** Marks the error as unhandled (it crashed a thread or request). */
  public EventBuilder markAsUnhandledError(@Nullable String submissionMethod) {
    contextData.markAsUnhandledError();
    if (submissionMethod != null) {
      contextData.setSubmissionMethod(submissionMethod);
    }
    return this;
  }

  /** Puts a value in the context data, which plugins can read but is not sent to the server. */
  public EventBuilder setContextProperty(String name, @Nullable Object value) {
    contextData.put(name, value);
    return this;
  }

  /**
   * Runs the event through the plugins and queues it. Returns the plugin context; check {@link
   * EventPluginContext#isCancelled()} to see whether it was discarded.
   */
  public EventPluginContext submit() {
    return client.submitEvent(event, contextData);
  }

  @Override
  public String toString() {
    return "EventBuilder{" + event + "}";
  }
}
