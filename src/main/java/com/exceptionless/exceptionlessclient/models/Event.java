package com.exceptionless.exceptionlessclient.models;

import com.exceptionless.exceptionlessclient.models.base.Model;
import com.exceptionless.exceptionlessclient.models.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.models.enums.EventTag;
import com.exceptionless.exceptionlessclient.models.services.EnvironmentInfo;
import com.exceptionless.exceptionlessclient.models.services.RequestInfo;
import com.exceptionless.exceptionlessclient.models.services.error.Error;
import com.exceptionless.exceptionlessclient.queue.EventDataFilter;
import com.exceptionless.exceptionlessclient.queue.EventValidator;
import com.exceptionless.exceptionlessclient.utils.Utils;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

// WARNING: `SuperBuilder` will not work for any class extending this. This class breaks the chain
// for customization
@Data
@EqualsAndHashCode(callSuper = true)
public class Event extends Model {
  private static final Integer DEFAULT_COUNT = 1;

  private String type;
  private String source;
  private LocalDate date;
  private Set<String> tags;
  private String message;
  private String geo;
  private long value;
  private String referenceId;
  private Long count;

  @Builder(builderClassName = "EventInternalBuilder")
  public Event(
      String type,
      String source,
      LocalDate date,
      Set<String> tags,
      String message,
      String geo,
      long value,
      String referenceId,
      Long count,
      Map<String, Object> data,
      Set<String> dataExclusions) {
    this.type = type;
    this.source = source;
    this.date = date == null ? LocalDate.now() : date;
    this.tags = tags == null ? new HashSet<>() : tags;
    this.message = message;
    this.geo = geo;
    this.value = value;
    this.referenceId = referenceId == null ? getDefaultReferenceId() : referenceId;
    this.count = count == null ? DEFAULT_COUNT : count;
    initData(data == null ? new HashMap<>() : data, dataExclusions);
  }

  private String getDefaultReferenceId() {
    return String.format("%s-%s", Thread.currentThread().getId(), UUID.randomUUID());
  }

  private void initData(Map<String, Object> data, Set<String> dataExclusions) {
    EventDataFilter eventDataFilter = EventDataFilter.builder().exclusions(dataExclusions).build();
    this.data =
        data.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, entry -> eventDataFilter.filter(entry.getValue())));
  }

  public void addData(Map<String, Object> data, Set<String> dataExclusions) {
    EventDataFilter filter = EventDataFilter.builder().exclusions(dataExclusions).build();
    data =
        data.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> filter.filter(entry.getValue())));
    this.data.putAll(data);
  }

  public void addTags(String... tags) {
    this.tags.addAll(Arrays.asList(tags));
  }

  public void addError(Error error) {
    data.put(EventPropertyKey.ERROR.value(), error);
  }

  public void addEnvironmentInfo(EnvironmentInfo environmentInfo) {
    data.put(EventPropertyKey.ENVIRONMENT.value(), environmentInfo);
  }

  public void addSubmissionMethod(String submissionMethod) {
    data.put(EventPropertyKey.SUBMISSION_METHOD.value(), submissionMethod);
  }

  public void addRequestInfo(RequestInfo requestInfo) {
    data.put(EventPropertyKey.REQUEST_INFO.value(), requestInfo);
  }

  public Optional<Error> getError() {
    return Optional.ofNullable(
        Utils.safeGetAs(data.get(EventPropertyKey.ERROR.value()), Error.class));
  }

  public Optional<EnvironmentInfo> getEnvironmentInfo() {
    return Optional.ofNullable(
        Utils.safeGetAs(data.get(EventPropertyKey.ENVIRONMENT.value()), EnvironmentInfo.class));
  }

  public Optional<String> getLogLevel() {
    return Optional.ofNullable(
        Utils.safeGetAs(data.get(EventPropertyKey.LOG_LEVEL.value()), String.class));
  }

  public Optional<UserInfo> getUserInfo() {
    return Optional.ofNullable(
        Utils.safeGetAs(data.get(EventPropertyKey.USER.value()), UserInfo.class));
  }

  public Optional<RequestInfo> getRequestInfo() {
    return Optional.ofNullable(
        Utils.safeGetAs(data.get(EventPropertyKey.REQUEST_INFO.value()), RequestInfo.class));
  }

  public Optional<String> getSubmissionMethod() {
    return Optional.ofNullable(
        Utils.safeGetAs(data.get(EventPropertyKey.SUBMISSION_METHOD.value()), String.class));
  }

  public static EventBuilder builder() {
    return new EventBuilder();
  }

  public static final class EventBuilder extends EventInternalBuilder {
    @Override
    public EventBuilder referenceId(String referenceId) {
      EventValidator.validateIdentifier(referenceId);
      super.referenceId(referenceId);
      return this;
    }

    public EventBuilder eventReference(String name, String id) {
      EventValidator.validateIdentifier(id);
      return property(String.format("%s:%s", EventPropertyKey.REF.value(), name), id);
    }

    public EventBuilder property(String name, Object value) {
      if (super.data == null) {
        super.data(new HashMap<>());
      }

      super.data.put(name, value);
      return this;
    }

    @Override
    public EventBuilder type(String type) {
      super.type(type);
      return this;
    }

    @Override
    public EventBuilder source(String source) {
      super.source(source);
      return this;
    }

    @Override
    public EventBuilder date(LocalDate date) {
      super.date(date);
      return this;
    }

    @Override
    public EventBuilder tags(Set<String> tags) {
      super.tags(tags);
      return this;
    }

    public EventBuilder tags(String... tags) {
      return tags(new HashSet<>(Arrays.asList(tags)));
    }

    public EventBuilder markAsCritical() {
      if (super.tags == null) {
        super.tags = new HashSet<>();
      }

      super.tags.add(EventTag.CRITICAL.value());
      return this;
    }

    @Override
    public EventBuilder message(String message) {
      super.message(message);
      return this;
    }

    public EventBuilder geo(int latitude, int longitude) {
      EventValidator.validateGeo(latitude, longitude);
      return geo(String.format("%s,%s", latitude, longitude));
    }

    @Override
    public EventBuilder geo(String geo) {
      super.geo(geo);
      return this;
    }

    @Override
    public EventBuilder value(long value) {
      super.value(value);
      return this;
    }

    @Override
    public EventBuilder count(Long count) {
      super.count(count);
      return this;
    }

    @Override
    public EventBuilder data(Map<String, Object> data) {
      super.data(data);
      return this;
    }

    @Override
    public EventBuilder dataExclusions(Set<String> dataExclusions) {
      super.dataExclusions(dataExclusions);
      return this;
    }

    public EventBuilder userIdentity(UserInfo userInfo) {
      return property(EventPropertyKey.USER.value(), userInfo);
    }

    public EventBuilder userIdentity(String identity) {
      return userIdentity(UserInfo.builder().identity(identity).build());
    }

    public EventBuilder userIdentity(String identity, String name) {
      return userIdentity(UserInfo.builder().identity(identity).name(name).build());
    }

    public EventBuilder userDescription(String emailAddress, String descripton) {
      return property(
          EventPropertyKey.USER_DESCRIPTION.value(),
          UserDescription.builder().emailAddress(emailAddress).description(descripton).build());
    }

    public EventBuilder manualStackingInfo(Map<String, String> signatureData, String title) {
      return property(
          EventPropertyKey.STACK.value(),
          ManualStackingInfo.builder().title(title).signatureData(signatureData).build());
    }

    public EventBuilder manualStackingKey(String manualStackingKey, String title) {
      return manualStackingInfo(Map.of("ManualStackingKey", manualStackingKey), title);
    }
  }
}
