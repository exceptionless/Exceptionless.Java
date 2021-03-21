package com.prashantchaubey.exceptionlessclient.models;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.base.Model;
import com.prashantchaubey.exceptionlessclient.models.services.RequestInfo;
import com.prashantchaubey.exceptionlessclient.plugins.ContextData;
import com.prashantchaubey.exceptionlessclient.queue.EventDataFilter;
import com.prashantchaubey.exceptionlessclient.queue.EventValidator;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.*;

@SuperBuilder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class Event extends Model {
  private final String type;
  private final String source;
  private final LocalDate date;
  private final Set<String> tags;
  private final String message;
  private final String geo;
  private final long value;
  private final String referenceId;
  private final Long count;

  public static EventBuilder<?, ?> builder(
      ContextData contextData, ConfigurationManager configurationManager) {
    return new EventBuilderImpl(contextData, configurationManager);
  }

  private static final class EventBuilderImpl extends EventBuilder<Event, EventBuilderImpl> {

    private ContextData contextData;
    private EventDataFilter eventDataFilter;
    // We can't access properties of parent class from the child class builder using lobmok. So this
    // object will keep track of it
    private Map<String, Object> data = new HashMap<>();

    EventBuilderImpl(ContextData contextData, ConfigurationManager configurationManager) {
      this.contextData = contextData;
      this.eventDataFilter =
          EventDataFilter.builder().exclusions(configurationManager.getDataExclusions()).build();
    }

    @Override
    public EventBuilderImpl referenceId(String referenceId) {
      EventValidator.validateIdentifier(referenceId);

      return super.referenceId(referenceId);
    }

    public EventBuilderImpl eventReference(String name, String id) {
      EventValidator.validateIdentifier(id);

      return property(String.format("@ref:%s", name), id);
    }

    private EventBuilderImpl property(String name, Object value) {
      value = eventDataFilter.filter(value);
      data.put(name, value);
      return super.data(data);
    }

    public EventBuilderImpl geo(int latitude, int longitude) {
      EventValidator.validateGeo(latitude, longitude);

      return super.geo(String.format("%s,%s", latitude, longitude));
    }

    public EventBuilderImpl userIdentity(UserInfo userInfo) {
      return property("@user", userInfo);
    }

    public EventBuilderImpl userIdentity(String identity) {
      return userIdentity(UserInfo.builder().identity(identity).build());
    }

    public EventBuilderImpl userIdentity(String identity, String name) {
      return userIdentity(UserInfo.builder().identity(identity).name(name).build());
    }

    public EventBuilderImpl userDescription(String emailAddress, String descripton) {
      return property(
          "@user_description",
          UserDescription.builder().emailAddress(emailAddress).description(descripton).build());
    }

    public EventBuilderImpl manualStackingInfo(Map<String, String> signatureData, String title) {
      return property(
          "@stack", ManualStackingInfo.builder().title(title).signatureData(signatureData).build());
    }

    public EventBuilderImpl manualStackingKey(String manualStackingKey, String title) {
      return manualStackingInfo(Map.of("ManualStackingKey", manualStackingKey), title);
    }

    public EventBuilderImpl addTags(String... tags) {
      Set<String> tagSet = new HashSet<>(Arrays.asList(tags));
      tagSet.addAll(super.tags);
      return super.tags(tagSet);
    }

    public EventBuilderImpl markAsCritical() {
      return addTags("Critical");
    }

    public EventBuilderImpl requestInfo(RequestInfo requestInfo) {
      contextData.addRequestInfo(requestInfo);
      return this;
    }

    @Override
    public EventBuilderImpl data(Map<String, Object> data) {
      this.data = data;
      return super.data(data);
    }

    @Override
    public Event build() {
      return new Event(this);
    }
  }
}
