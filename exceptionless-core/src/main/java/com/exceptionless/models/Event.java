package com.exceptionless.models;

import com.exceptionless.json.JsonSerializable;
import com.exceptionless.json.JsonWriter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
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

  public @Nullable String getType() {
    return type;
  }

  public Event setType(@Nullable String type) {
    this.type = type;
    return this;
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

  public Event setSource(@Nullable String source) {
    this.source = source;
    return this;
  }

  public @Nullable OffsetDateTime getDate() {
    return date;
  }

  public Event setDate(@Nullable OffsetDateTime date) {
    this.date = date;
    return this;
  }

  /** The event's tags. The set is live and may be modified. */
  public Set<String> getTags() {
    return tags;
  }

  public Event addTags(String... tags) {
    for (String tag : tags) {
      addTag(tag);
    }
    return this;
  }

  public Event addTags(Collection<String> tags) {
    for (String tag : tags) {
      addTag(tag);
    }
    return this;
  }

  private void addTag(@Nullable String tag) {
    if (tag == null) {
      return;
    }
    String trimmed = tag.trim();
    if (!trimmed.isEmpty()) {
      tags.add(trimmed);
    }
  }

  public @Nullable String getMessage() {
    return message;
  }

  public Event setMessage(@Nullable String message) {
    this.message = message;
    return this;
  }

  /** The location as {@code "latitude,longitude"}. */
  public @Nullable String getGeo() {
    return geo;
  }

  public Event setGeo(@Nullable String geo) {
    this.geo = geo;
    return this;
  }

  public @Nullable BigDecimal getValue() {
    return value;
  }

  public Event setValue(@Nullable BigDecimal value) {
    this.value = value;
    return this;
  }

  public @Nullable Integer getCount() {
    return count;
  }

  public Event setCount(@Nullable Integer count) {
    this.count = count;
    return this;
  }

  /** Extended data. The map is live and may be modified. */
  public Map<String, Object> getData() {
    return data;
  }

  public @Nullable String getReferenceId() {
    return referenceId;
  }

  public Event setReferenceId(@Nullable String referenceId) {
    this.referenceId = referenceId;
    return this;
  }

  public @Nullable ErrorInfo getError() {
    return data.get(KnownDataKeys.ERROR) instanceof ErrorInfo error ? error : null;
  }

  public Event setError(@Nullable ErrorInfo error) {
    return putOrRemove(KnownDataKeys.ERROR, error);
  }

  public @Nullable RequestInfo getRequestInfo() {
    return data.get(KnownDataKeys.REQUEST_INFO) instanceof RequestInfo request ? request : null;
  }

  public Event setRequestInfo(@Nullable RequestInfo requestInfo) {
    return putOrRemove(KnownDataKeys.REQUEST_INFO, requestInfo);
  }

  public @Nullable EnvironmentInfo getEnvironmentInfo() {
    return data.get(KnownDataKeys.ENVIRONMENT_INFO) instanceof EnvironmentInfo env ? env : null;
  }

  public Event setEnvironmentInfo(@Nullable EnvironmentInfo environmentInfo) {
    return putOrRemove(KnownDataKeys.ENVIRONMENT_INFO, environmentInfo);
  }

  public @Nullable UserInfo getUserInfo() {
    return data.get(KnownDataKeys.USER_INFO) instanceof UserInfo user ? user : null;
  }

  public Event setUserInfo(@Nullable UserInfo userInfo) {
    return putOrRemove(KnownDataKeys.USER_INFO, userInfo);
  }

  public @Nullable UserDescription getUserDescription() {
    return data.get(KnownDataKeys.USER_DESCRIPTION) instanceof UserDescription d ? d : null;
  }

  public Event setUserDescription(@Nullable UserDescription userDescription) {
    return putOrRemove(KnownDataKeys.USER_DESCRIPTION, userDescription);
  }

  public @Nullable ManualStackingInfo getManualStackingInfo() {
    return data.get(KnownDataKeys.MANUAL_STACKING_INFO) instanceof ManualStackingInfo info
        ? info
        : null;
  }

  public Event setManualStackingInfo(@Nullable ManualStackingInfo info) {
    return putOrRemove(KnownDataKeys.MANUAL_STACKING_INFO, info);
  }

  /** The log level ({@code @level}), e.g. {@code "error"}. */
  public @Nullable String getLevel() {
    return data.get(KnownDataKeys.LEVEL) instanceof String level ? level : null;
  }

  public Event setLevel(@Nullable String level) {
    return putOrRemove(KnownDataKeys.LEVEL, level);
  }

  public @Nullable String getVersion() {
    return data.get(KnownDataKeys.VERSION) instanceof String version ? version : null;
  }

  public Event setVersion(@Nullable String version) {
    return putOrRemove(KnownDataKeys.VERSION, version);
  }

  public @Nullable String getSubmissionMethod() {
    return data.get(KnownDataKeys.SUBMISSION_METHOD) instanceof String method ? method : null;
  }

  public Event setSubmissionMethod(@Nullable String submissionMethod) {
    return putOrRemove(KnownDataKeys.SUBMISSION_METHOD, submissionMethod);
  }

  /** The id of the session this event belongs to ({@code @ref:session}). */
  public @Nullable String getSessionId() {
    return getEventReference("session");
  }

  public @Nullable String getEventReference(String name) {
    return data.get(KnownDataKeys.REFERENCE_PREFIX + name) instanceof String id ? id : null;
  }

  public Event setEventReference(String name, @Nullable String id) {
    return putOrRemove(KnownDataKeys.REFERENCE_PREFIX + name, id);
  }

  private Event putOrRemove(String key, @Nullable Object value) {
    if (value == null) {
      data.remove(key);
    } else {
      data.put(key, value);
    }
    return this;
  }

  @Override
  public void toJson(JsonWriter writer) {
    writer
        .beginObject()
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
