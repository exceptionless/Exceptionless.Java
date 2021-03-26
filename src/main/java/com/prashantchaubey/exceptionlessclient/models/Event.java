package com.prashantchaubey.exceptionlessclient.models;

import com.prashantchaubey.exceptionlessclient.models.base.Model;
import com.prashantchaubey.exceptionlessclient.models.enums.EventPropertyKey;
import com.prashantchaubey.exceptionlessclient.models.enums.EventTag;
import com.prashantchaubey.exceptionlessclient.models.services.EnvironmentInfo;
import com.prashantchaubey.exceptionlessclient.models.services.error.Error;
import com.prashantchaubey.exceptionlessclient.queue.EventDataFilter;
import com.prashantchaubey.exceptionlessclient.queue.EventValidator;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.prashantchaubey.exceptionlessclient.utils.EventUtils.safeGetAs;

@SuperBuilder
@Getter
public class Event extends Model {
  @Setter private String type;
  private String source;
  @Setter private LocalDate date;
  private Set<String> tags;
  private String message;
  private String geo;
  private long value;
  @Setter private String referenceId;
  @Setter private Long count;

  public void addTags(String... tags) {
    this.tags.addAll(Arrays.asList(tags));
  }

  public void addData(Map<String, Object> data) {
    addData(data, new HashSet<>());
  }

  public void addData(Map<String, Object> data, Set<String> dataExclusions) {
    EventDataFilter filter = EventDataFilter.builder().exclusions(dataExclusions).build();
    data =
        data.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> filter.filter(entry.getValue())));
    this.data.putAll(data);
  }

  public void addEnvironmentInfo(EnvironmentInfo environmentInfo) {
    data.put(EventPropertyKey.ENVIRONMENT.value(), environmentInfo);
  }

  public Error getError() {
    return safeGetAs(data.get(EventPropertyKey.ERROR.value()), Error.class);
  }

  public void addSubmissionMethod(String submissionMethod) {
    data.put(EventPropertyKey.SUBMISSION_METHOD.value(), submissionMethod);
  }

  public static EventBuilderImpl builder(Set<String> dataExclusions) {
    return new EventBuilderImpl(dataExclusions);
  }

  public static final class EventBuilderImpl extends EventBuilder<Event, EventBuilderImpl> {
    private EventDataFilter eventDataFilter;
    // lombok builder create private fields in the builder class so we can't access `data` from
    // `Model` even though it is `protected`. So we will use this object as an proxy.
    private Map<String, Object> data = new HashMap<>();

    EventBuilderImpl(Set<String> dataExclusions) {
      this.eventDataFilter = EventDataFilter.builder().exclusions(dataExclusions).build();
    }

    @Override
    public EventBuilderImpl referenceId(String referenceId) {
      EventValidator.validateIdentifier(referenceId);

      return super.referenceId(referenceId);
    }

    public EventBuilderImpl eventReference(String name, String id) {
      EventValidator.validateIdentifier(id);
      return property(String.format("%s:%s", EventPropertyKey.REF.value(), name), id);
    }

    public EventBuilderImpl property(String name, Object value) {
      value = eventDataFilter.filter(value);
      data.put(name, value);
      return super.data(data);
    }

    public EventBuilderImpl geo(int latitude, int longitude) {
      EventValidator.validateGeo(latitude, longitude);

      return super.geo(String.format("%s,%s", latitude, longitude));
    }

    public EventBuilderImpl userIdentity(UserInfo userInfo) {
      return property(EventPropertyKey.USER.value(), userInfo);
    }

    public EventBuilderImpl userIdentity(String identity) {
      return userIdentity(UserInfo.builder().identity(identity).build());
    }

    public EventBuilderImpl userIdentity(String identity, String name) {
      return userIdentity(UserInfo.builder().identity(identity).name(name).build());
    }

    public EventBuilderImpl userDescription(String emailAddress, String descripton) {
      return property(
          EventPropertyKey.USER_DESCRIPTION.value(),
          UserDescription.builder().emailAddress(emailAddress).description(descripton).build());
    }

    public EventBuilderImpl manualStackingInfo(Map<String, String> signatureData, String title) {
      return property(
          EventPropertyKey.STACK.value(),
          ManualStackingInfo.builder().title(title).signatureData(signatureData).build());
    }

    public EventBuilderImpl manualStackingKey(String manualStackingKey, String title) {
      return manualStackingInfo(Map.of("ManualStackingKey", manualStackingKey), title);
    }

    public EventBuilderImpl addTags(String... tags) {
      super.tags.addAll(new HashSet<>(Arrays.asList(tags)));
      return this;
    }

    public EventBuilderImpl markAsCritical() {
      return addTags(EventTag.CRITICAL.value());
    }

    @Override
    public EventBuilderImpl data(Map<String, Object> data) {
      data =
          data.entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey, entry -> eventDataFilter.filter(entry.getValue())));
      this.data = data;
      return super.data(data);
    }

    @Override
    public Event build() {
      return new Event(this);
    }
  }
}
