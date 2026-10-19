package com.exceptionless.models;

/** Data keys with special meaning to the Exceptionless server. */
public final class KnownDataKeys {
  public static final String ERROR = "@error";
  public static final String SIMPLE_ERROR = "@simple_error";
  public static final String REQUEST_INFO = "@request";
  public static final String TRACE_LOG = "@trace";
  public static final String ENVIRONMENT_INFO = "@environment";
  public static final String USER_INFO = "@user";
  public static final String USER_DESCRIPTION = "@user_description";
  public static final String VERSION = "@version";
  public static final String LEVEL = "@level";
  public static final String SUBMISSION_METHOD = "@submission_method";
  public static final String MANUAL_STACKING_INFO = "@stack";

  /** Prefix for event references, e.g. {@code @ref:session}. */
  public static final String REFERENCE_PREFIX = "@ref:";

  /** Extra exception properties inside {@code @error.data}. */
  public static final String EXTENDED_DATA = "@ext";

  private KnownDataKeys() {}
}
