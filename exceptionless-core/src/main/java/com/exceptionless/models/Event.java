package com.exceptionless.models;

import com.exceptionless.internal.DeploymentEnvironment;
import com.exceptionless.json.JsonSerializable;
import com.exceptionless.json.JsonWriter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * An Exceptionless event. Events are mutable while they move through the plugin pipeline and are
 * serialized when they are queued.
 */
public final class Event implements JsonSerializable {
  private @Nullable String environment;
  private @Nullable String type;
  private @Nullable String source;
  private @Nullable OffsetDateTime date;
  private final Set<String> tags = new LinkedHashSet<>();
  private @Nullable String message;
  private @Nullable String geo;
  private @Nullable BigDecimal value;
  private @Nullable Integer count;
  private final Map<String, Object> data = new LinkedHashMap<>();
  private @Nullable String referenceId;

  /**
   * The deployment environment, such as {@code production} or {@code staging}. This is the event's
   * root {@code environment} property, not the machine details in {@code @environment}. See {@link
   * #getEnvironmentInfo()} for those.
   */
  public @Nullable String getEnvironment() {
    return environment;
  }

  /**
   * Sets the deployment environment. The name is trimmed and keeps its casing. Empty names, names
   * longer than 64 characters and names with control characters are not valid and clear the
   * environment, so the event is sent without one.
   */
  public void setEnvironment(@Nullable String environment) {
    this.environment = DeploymentEnvironment.normalize(environment);
  }

  public @Nullable String getType() {
    return type;
  }

  public void setType(@Nullable String type) {
    this.type = type;
  }

  public boolean isError() {
    return EventTypes.ERROR.equals(type);
  }

  public boolean isLog() {
    return EventTypes.LOG.equals(type);
  }

  public boolean isSessionStart() {
    return EventTypes.SESSION.equals(type);
  }

  public boolean isSessionEnd() {
    return EventTypes.SESSION_END.equals(type);
  }

  public boolean isNotFound() {
    return EventTypes.NOT_FOUND.equals(type);
  }

  public @Nullable String getSource() {
    return source;
  }

  public void setSource(@Nullable String source) {
    this.source = source;
  }

  public @Nullable OffsetDateTime getDate() {
    return date;
  }

  public void setDate(@Nullable OffsetDateTime date) {
    this.date = date;
  }

  /**
   * The event's tags, as a read-only view. Use {@link #addTags(String...)} and {@link
   * #removeTags(String...)} to change them.
   */
  public Set<String> getTags() {
    return Collections.unmodifiableSet(tags);
  }

  /** Whether the event has the tag. */
  public boolean hasTag(@Nullable String tag) {
    String stripped = normalizeTag(tag);
    return stripped != null && tags.contains(stripped);
  }

  /** Adds tags. Surrounding whitespace is removed and blank tags are ignored. */
  public void addTags(String... tags) {
    addTags(Arrays.asList(tags));
  }

  public void addTags(Collection<String> tags) {
    for (String tag : tags) {
      String stripped = normalizeTag(tag);
      if (stripped != null) {
        this.tags.add(stripped);
      }
    }
  }

  public void removeTags(String... tags) {
    for (String tag : tags) {
      String stripped = normalizeTag(tag);
      if (stripped != null) {
        this.tags.remove(stripped);
      }
    }
  }

  private static @Nullable String normalizeTag(@Nullable String tag) {
    if (tag == null) {
      return null;
    }
    String stripped = tag.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  public @Nullable String getMessage() {
    return message;
  }

  public void setMessage(@Nullable String message) {
    this.message = message;
  }

  /** {@return the location as {@code "latitude,longitude"}} */
  public @Nullable String getGeo() {
    return geo;
  }

  public void setGeo(@Nullable String geo) {
    this.geo = geo;
  }

  public @Nullable BigDecimal getValue() {
    return value;
  }

  public void setValue(@Nullable BigDecimal value) {
    this.value = value;
  }

  public @Nullable Integer getCount() {
    return count;
  }

  public void setCount(@Nullable Integer count) {
    this.count = count;
  }

  /** Extended data. The map is live and may be modified. */
  public Map<String, Object> getData() {
    return data;
  }

  public @Nullable String getReferenceId() {
    return referenceId;
  }

  public void setReferenceId(@Nullable String referenceId) {
    this.referenceId = referenceId;
  }

  public @Nullable ErrorInfo getError() {
    return data.get(KnownDataKeys.ERROR) instanceof ErrorInfo error ? error : null;
  }

  public void setError(@Nullable ErrorInfo error) {
    putOrRemove(KnownDataKeys.ERROR, error);
  }

  public @Nullable RequestInfo getRequestInfo() {
    return data.get(KnownDataKeys.REQUEST_INFO) instanceof RequestInfo request ? request : null;
  }

  public void setRequestInfo(@Nullable RequestInfo requestInfo) {
    putOrRemove(KnownDataKeys.REQUEST_INFO, requestInfo);
  }

  public @Nullable EnvironmentInfo getEnvironmentInfo() {
    return data.get(KnownDataKeys.ENVIRONMENT_INFO) instanceof EnvironmentInfo env ? env : null;
  }

  public void setEnvironmentInfo(@Nullable EnvironmentInfo environmentInfo) {
    putOrRemove(KnownDataKeys.ENVIRONMENT_INFO, environmentInfo);
  }

  public @Nullable UserInfo getUserInfo() {
    return data.get(KnownDataKeys.USER_INFO) instanceof UserInfo user ? user : null;
  }

  public void setUserInfo(@Nullable UserInfo userInfo) {
    putOrRemove(KnownDataKeys.USER_INFO, userInfo);
  }

  public @Nullable UserDescription getUserDescription() {
    return data.get(KnownDataKeys.USER_DESCRIPTION) instanceof UserDescription d ? d : null;
  }

  public void setUserDescription(@Nullable UserDescription userDescription) {
    putOrRemove(KnownDataKeys.USER_DESCRIPTION, userDescription);
  }

  public @Nullable ManualStackingInfo getManualStackingInfo() {
    return data.get(KnownDataKeys.MANUAL_STACKING_INFO) instanceof ManualStackingInfo info
        ? info
        : null;
  }

  public void setManualStackingInfo(@Nullable ManualStackingInfo info) {
    putOrRemove(KnownDataKeys.MANUAL_STACKING_INFO, info);
  }

  /** The log level ({@code @level}), e.g. {@code "error"}. */
  public @Nullable String getLevel() {
    return data.get(KnownDataKeys.LEVEL) instanceof String level ? level : null;
  }

  public void setLevel(@Nullable String level) {
    putOrRemove(KnownDataKeys.LEVEL, level);
  }

  public @Nullable String getVersion() {
    return data.get(KnownDataKeys.VERSION) instanceof String version ? version : null;
  }

  public void setVersion(@Nullable String version) {
    putOrRemove(KnownDataKeys.VERSION, version);
  }

  public @Nullable String getSubmissionMethod() {
    return data.get(KnownDataKeys.SUBMISSION_METHOD) instanceof String method ? method : null;
  }

  public void setSubmissionMethod(@Nullable String submissionMethod) {
    putOrRemove(KnownDataKeys.SUBMISSION_METHOD, submissionMethod);
  }

  /** {@return the id of the session this event belongs to ({@code @ref:session})} */
  public @Nullable String getSessionId() {
    return getEventReference("session");
  }

  public @Nullable String getEventReference(String name) {
    return data.get(KnownDataKeys.REFERENCE_PREFIX + name) instanceof String id ? id : null;
  }

  public void setEventReference(String name, @Nullable String id) {
    putOrRemove(KnownDataKeys.REFERENCE_PREFIX + name, id);
  }

  private void putOrRemove(String key, @Nullable Object value) {
    if (value == null) {
      data.remove(key);
    } else {
      data.put(key, value);
    }
  }

  @Override
  public void toJson(JsonWriter writer) {
    writer
        .beginObject()
        .fieldIfNotEmpty("environment", environment)
        .fieldIfNotEmpty("type", type)
        .fieldIfNotEmpty("source", source)
        .field("date", date)
        .fieldIfNotEmpty("tags", tags)
        .fieldIfNotEmpty("message", message)
        .fieldIfNotEmpty("geo", geo)
        .field("value", value)
        .field("count", count)
        .fieldIfNotEmpty("data", data)
        .fieldIfNotEmpty("reference_id", referenceId)
        .endObject();
  }

  @Override
  public String toString() {
    return "Event{type=" + type + ", source=" + source + ", message=" + message + "}";
  }
}
