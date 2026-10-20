package com.exceptionless.models;

/** Built-in event types understood by the Exceptionless server. Custom types are allowed. */
public final class EventTypes {
  public static final String ERROR = "error";
  public static final String FEATURE_USAGE = "usage";
  public static final String LOG = "log";
  public static final String NOT_FOUND = "404";
  public static final String SESSION = "session";
  public static final String SESSION_END = "sessionend";
  public static final String SESSION_HEARTBEAT = "heartbeat";

  private EventTypes() {}
}
