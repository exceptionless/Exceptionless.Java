package com.exceptionless.servlet;

import com.exceptionless.EventBuilder;
import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.models.UserInfo;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * Reports unhandled exceptions and 404 responses to Exceptionless, with the request details, the
 * signed in user ({@link HttpServletRequest#getUserPrincipal()}, when {@code includeUserName} is
 * on) and {@code @submission_method} {@value #SUBMISSION_METHOD}.
 *
 * <p>While a request runs, the filter also makes it available to {@link ServletRequestInfoPlugin},
 * so errors logged during the request (through an Exceptionless logging appender, or submitted
 * directly) get the request details too. An exception that is both logged and thrown out of the
 * filter is only reported once.
 *
 * <p>Register it first in the chain so it sees the exceptions of every other filter. In {@code
 * web.xml}:
 *
 * <pre>{@code
 * <filter>
 *   <filter-name>exceptionless</filter-name>
 *   <filter-class>com.exceptionless.servlet.ExceptionlessFilter</filter-class>
 *   <async-supported>true</async-supported>
 *   <init-param>
 *     <param-name>captureNotFound</param-name>
 *     <param-value>true</param-value>
 *   </init-param>
 * </filter>
 * <filter-mapping>
 *   <filter-name>exceptionless</filter-name>
 *   <url-pattern>/*</url-pattern>
 * </filter-mapping>
 * }</pre>
 *
 * Or in code, with your own client:
 *
 * <pre>{@code
 * servletContext.addFilter("exceptionless", new ExceptionlessFilter(client))
 *     .addMappingForUrlPatterns(null, false, "/*");
 * }</pre>
 *
 * <p>Without a client, the filter uses {@link Exceptionless#getDefault()}, looked up on the first
 * request. Exceptions are always rethrown unchanged, so the container's error handling still runs.
 *
 * <p><b>Async requests:</b> when the request goes async ({@link HttpServletRequest#startAsync()}),
 * the 404 check waits until the async request completes, and errors reported to {@link
 * AsyncListener#onError} are submitted as unhandled. Errors logged on application threads during
 * async processing don't get request details, because the request is only tracked on the container
 * threads that run the filter.
 */
public class ExceptionlessFilter implements Filter {
  /** The {@code @submission_method} of the events reported by this filter. */
  public static final String SUBMISSION_METHOD = "servletFilter";

  /** The init parameter that turns 404 reporting on or off ({@code true} by default). */
  public static final String CAPTURE_NOT_FOUND_PARAM = "captureNotFound";

  private static final System.Logger LOG = System.getLogger(ExceptionlessFilter.class.getName());
  private static final String HANDLED_ATTRIBUTE = ExceptionlessFilter.class.getName() + ".HANDLED";
  private static final String ASYNC_ATTRIBUTE = ExceptionlessFilter.class.getName() + ".ASYNC";

  private volatile @Nullable ExceptionlessClient client;
  private volatile boolean captureNotFound = true;

  /** Uses {@link Exceptionless#getDefault()}. */
  public ExceptionlessFilter() {}

  /** Reports to {@code client}. */
  public ExceptionlessFilter(ExceptionlessClient client) {
    this.client = client;
  }

  @Override
  public void init(FilterConfig filterConfig) {
    String value = filterConfig.getInitParameter(CAPTURE_NOT_FOUND_PARAM);
    if (value != null && !value.isBlank()) {
      captureNotFound = Boolean.parseBoolean(value.trim());
    }
  }

  /** Whether 404 responses are reported. */
  public boolean isCaptureNotFound() {
    return captureNotFound;
  }

  /** Turns 404 reporting on (the default) or off. */
  public ExceptionlessFilter setCaptureNotFound(boolean captureNotFound) {
    this.captureNotFound = captureNotFound;
    return this;
  }

  /** The client events are reported to. */
  public ExceptionlessClient getClient() {
    ExceptionlessClient current = client;
    if (current == null) {
      current = Exceptionless.getDefault();
      client = current;
    }
    registerPlugin(current.getConfiguration());
    return current;
  }

  private static void registerPlugin(ExceptionlessConfiguration config) {
    if (!config.hasPlugin(ServletRequestInfoPlugin.NAME)) {
      config.addPlugin(new ServletRequestInfoPlugin());
    }
  }

  @Override
  public void doFilter(
      ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
      throws IOException, ServletException {
    if (!(servletRequest instanceof HttpServletRequest request)
        || !(servletResponse instanceof HttpServletResponse response)) {
      chain.doFilter(servletRequest, servletResponse);
      return;
    }

    ExceptionlessClient exceptionless = getClient();
    // Only the first REQUEST dispatch checks for 404s, even if the filter is registered twice.
    boolean checkNotFound =
        request.getDispatcherType() == DispatcherType.REQUEST
            && request.getAttribute(HANDLED_ATTRIBUTE) == null;
    if (checkNotFound) {
      request.setAttribute(HANDLED_ATTRIBUTE, Boolean.TRUE);
    }

    HttpServletRequest previous = ServletRequestInfoPlugin.setCurrentRequest(request);
    try {
      chain.doFilter(request, response);
    } catch (Throwable error) {
      reportUnhandled(exceptionless, error, request);
      throw error;
    } finally {
      ServletRequestInfoPlugin.setCurrentRequest(previous);
    }

    if (!checkNotFound) {
      return;
    }
    if (request.isAsyncStarted()) {
      listenForAsyncCompletion(exceptionless, request, response);
    } else if (response.getStatus() == HttpServletResponse.SC_NOT_FOUND) {
      reportNotFound(exceptionless, request);
    }
  }

  private void listenForAsyncCompletion(
      ExceptionlessClient exceptionless, HttpServletRequest request, HttpServletResponse response) {
    if (request.getAttribute(ASYNC_ATTRIBUTE) != null) {
      return;
    }
    request.setAttribute(ASYNC_ATTRIBUTE, Boolean.TRUE);
    try {
      request
          .getAsyncContext()
          .addListener(new CompletionListener(exceptionless), request, response);
    } catch (IllegalStateException e) {
      LOG.log(System.Logger.Level.DEBUG, "Unable to listen for async completion", e);
    }
  }

  /** Reports an exception that escaped the filter chain. Never throws. */
  protected void reportUnhandled(
      ExceptionlessClient exceptionless, Throwable error, HttpServletRequest request) {
    try {
      EventBuilder builder =
          exceptionless
              .createUnhandledException(unwrap(error), SUBMISSION_METHOD)
              .setRequestInfo(ServletRequestInfoFactory.create(request));
      setUser(builder, request, exceptionless.getConfiguration());
      builder.submit();
    } catch (RuntimeException | LinkageError e) {
      LOG.log(System.Logger.Level.WARNING, "Unable to report unhandled exception", e);
    }
  }

  /** Reports a 404 response. Never throws. */
  protected void reportNotFound(ExceptionlessClient exceptionless, HttpServletRequest request) {
    if (!captureNotFound) {
      return;
    }
    try {
      EventBuilder builder =
          exceptionless
              .createNotFound(request.getRequestURI())
              .setSubmissionMethod(SUBMISSION_METHOD)
              .setRequestInfo(ServletRequestInfoFactory.create(request));
      setUser(builder, request, exceptionless.getConfiguration());
      builder.submit();
    } catch (RuntimeException | LinkageError e) {
      LOG.log(System.Logger.Level.WARNING, "Unable to report 404", e);
    }
  }

  private static void setUser(
      EventBuilder builder, HttpServletRequest request, ExceptionlessConfiguration config) {
    UserInfo user = ServletRequestInfoPlugin.userInfo(request, config);
    if (user != null) {
      builder.setUserIdentity(user);
    }
  }

  /**
   * The exception to report: {@link ServletException}s that only wrap another exception (as
   * frameworks such as Spring MVC do) are unwrapped to their root cause.
   */
  static Throwable unwrap(Throwable error) {
    Throwable current = error;
    for (int depth = 0;
        depth < 10 && current instanceof ServletException servletException;
        depth++) {
      Throwable cause = servletException.getRootCause();
      if (cause == null || cause == current) {
        break;
      }
      current = cause;
    }
    return current;
  }

  @Override
  public void destroy() {}

  private final class CompletionListener implements AsyncListener {
    private final ExceptionlessClient exceptionless;
    private volatile boolean failed;

    CompletionListener(ExceptionlessClient exceptionless) {
      this.exceptionless = exceptionless;
    }

    @Override
    public void onComplete(AsyncEvent event) {
      if (failed
          || !(event.getSuppliedRequest() instanceof HttpServletRequest request)
          || !(event.getSuppliedResponse() instanceof HttpServletResponse response)) {
        return;
      }
      if (response.getStatus() == HttpServletResponse.SC_NOT_FOUND) {
        reportNotFound(exceptionless, request);
      }
    }

    @Override
    public void onError(AsyncEvent event) {
      failed = true;
      Throwable error = event.getThrowable();
      if (error != null && event.getSuppliedRequest() instanceof HttpServletRequest request) {
        reportUnhandled(exceptionless, error, request);
      }
    }

    @Override
    public void onTimeout(AsyncEvent event) {}

    @Override
    public void onStartAsync(AsyncEvent event) {
      // The request went async again: keep listening for its completion.
      if (event.getAsyncContext() != null) {
        event
            .getAsyncContext()
            .addListener(this, event.getSuppliedRequest(), event.getSuppliedResponse());
      }
    }
  }
}
