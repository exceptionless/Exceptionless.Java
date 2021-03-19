package com.prashantchaubey.exceptionlessclient.lastreferenceidmanager;

public interface LastReferenceIdManagerIF {
    String getLast();
    void clearLast();
    void setLast(String eventId);
}
