package com.exceptionless.servlet;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.models.Event;
import com.exceptionless.models.EventTypes;
import com.exceptionless.models.UserInfo;
import com.exceptionless.plugins.EventPlugin;
import com.exceptionless.plugins.EventPluginContext;
import com.exceptionless.plugins.RequestInfoPlugin;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.jspecify.annotations.Nullable;

/**
 * Adds the request being handled on the current thread to error and 404 events that don't have
 * request info yet, for example an error logged with {@code log.error("Payment failed", e)} from a
 * controller. {@link ExceptionlessFilter} registers this plugin and tracks the current request.
 *
 * <p>Runs before the core {@link RequestInfoPlugin}, which applies the privacy settings and data
 * exclusions to the captured request. Form fields are not captured here: the application may not
 * have read the request body yet.
 */
public final class ServletRequestInfoPlugin implements EventPlugin {
  /** The plugin name, used to register it only once per client. */
  public static final String NAME = "ServletRequestInfoPlugin";

  /** Runs before {@link RequestInfoPlugin#PRIORITY} so the request is sanitized. */
  public static final int PRIORITY = RequestInfoPlugin.PRIORITY - 5;

  private static final ThreadLocal<@Nullable HttpServletRequest> CURRENT = new ThreadLocal<>();

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  /**
   * The request {@link ExceptionlessFilter} is handling on the current thread, or {@code null}
   * outside of a request.
   */
  public static @Nullable HttpServletRequest getCurrentRequest() {
    return CURRENT.get();
  }

  /** Sets the current request and returns the previous one, to restore afterwards. */
  static @Nullable HttpServletRequest setCurrentRequest(@Nullable HttpServletRequest request) {
    HttpServletRequest previous = CURRENT.get();
    if (request == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(request);
    }
    return previous;
  }

  @Override
  public void run(EventPluginContext context) {
    HttpServletRequest request = CURRENT.get();
    if (request == null) {
      return;
    }
    Event event = context.getEvent();
    if (!EventTypes.ERROR.equals(event.getType())
        && !EventTypes.NOT_FOUND.equals(event.getType())) {
      return;
    }
    if (event.getRequestInfo() == null && context.getContextData().getRequestInfo() == null) {
      context.getContextData().setRequestInfo(ServletRequestInfoFactory.create(request, false));
    }
    if (event.getUserInfo() == null) {
      UserInfo user = userInfo(request, context.getConfiguration());
      if (user != null) {
        event.setUserInfo(user);
      }
    }
  }

  /** The signed in user, when user names may be sent. */
  static @Nullable UserInfo userInfo(
      HttpServletRequest request, ExceptionlessConfiguration config) {
    if (!config.isIncludeUserName()) {
      return null;
    }
    Principal principal;
    try {
      principal = request.getUserPrincipal();
    } catch (RuntimeException e) {
      return null;
    }
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      return null;
    }
    return new UserInfo(principal.getName());
  }
}
