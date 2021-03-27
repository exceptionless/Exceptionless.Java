package com.prashantchaubey.exceptionlessclient.lastreferenceidmanager;

public class DefaultLastReferenceIdManager implements LastReferenceIdManagerIF {
  private String lastReferencedId;

  @Override
  public String getLast() {
    return lastReferencedId;
  }

  @Override
  public void clearLast() {
    lastReferencedId = null;
  }

  @Override
  public void setLast(String eventId) {
    lastReferencedId = eventId;
  }
}
