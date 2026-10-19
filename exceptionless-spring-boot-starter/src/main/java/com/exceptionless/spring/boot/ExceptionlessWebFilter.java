package com.exceptionless.spring.boot;

import com.exceptionless.EventBuilder;
import com.exceptionless.ExceptionlessClient;
import java.security.Principal;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Reports unhandled exceptions and 404s in Spring WebFlux applications, with the request details
 * and the signed in user ({@code includeUserName}). Registered automatically by the
 * auto-configuration in reactive web applications.
 *
 * <p>Errors that carry a 4xx status (such as {@code ResponseStatusException}) are not reported as
 * errors; a 404 is reported as a not found event. Errors are always passed on unchanged, so
 * WebFlux's error handling still runs.
 *
 * <p>Unlike the servlet filter, errors logged while a reactive request is processed don't get the
 * request details, because the request isn't bound to a thread.
 */
public class ExceptionlessWebFilter implements WebFilter, Ordered {
  /** The {@code @submission_method} of the events reported by this filter. */
  public static final String SUBMISSION_METHOD = "webFilter";

  private static final System.Logger LOG = System.getLogger(ExceptionlessWebFilter.class.getName());

  private final ExceptionlessClient client;
  private final boolean captureNotFound;
  private final int order;

  public ExceptionlessWebFilter(ExceptionlessClient client, boolean captureNotFound, int order) {
    this.client = client;
    this.captureNotFound = captureNotFound;
    this.order = order;
  }

  @Override
  public int getOrder() {
    return order;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    return chain
        .filter(exchange)
        .onErrorResume(error -> reportError(exchange, error).then(Mono.error(error)))
        .then(
            Mono.defer(
                () ->
                    isNotFound(exchange.getResponse().getStatusCode())
                        ? reportNotFound(exchange)
                        : Mono.empty()));
  }

  private Mono<Void> reportError(ServerWebExchange exchange, Throwable error) {
    if (error instanceof ErrorResponse response) {
      HttpStatusCode status = response.getStatusCode();
      if (isNotFound(status)) {
        return reportNotFound(exchange);
      }
      if (status.value() < 500) {
        return Mono.empty();
      }
    }
    return withUser(
        exchange,
        user ->
            submit(
                client.createUnhandledException(error, SUBMISSION_METHOD),
                exchange,
                user,
                "Unable to report unhandled exception"));
  }

  private Mono<Void> reportNotFound(ServerWebExchange exchange) {
    if (!captureNotFound) {
      return Mono.empty();
    }
    return withUser(
        exchange,
        user ->
            submit(
                client
                    .createNotFound(exchange.getRequest().getURI().getRawPath())
                    .setSubmissionMethod(SUBMISSION_METHOD),
                exchange,
                user,
                "Unable to report 404"));
  }

  private static boolean isNotFound(@Nullable HttpStatusCode status) {
    return status != null && status.value() == 404;
  }

  private Mono<Void> withUser(
      ServerWebExchange exchange, Function<Optional<String>, Mono<Void>> action) {
    if (!client.getConfiguration().isIncludeUserName()) {
      return Mono.defer(() -> action.apply(Optional.empty()));
    }
    return exchange
        .getPrincipal()
        .map(Principal::getName)
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .onErrorReturn(Optional.empty())
        .flatMap(action);
  }

  private static Mono<Void> submit(
      EventBuilder builder, ServerWebExchange exchange, Optional<String> user, String failure) {
    try {
      builder.setRequestInfo(ServerHttpRequestInfoFactory.create(exchange.getRequest()));
      user.filter(name -> !name.isBlank()).ifPresent(builder::setUserIdentity);
      builder.submit();
    } catch (RuntimeException | LinkageError e) {
      LOG.log(System.Logger.Level.WARNING, failure, e);
    }
    return Mono.empty();
  }
}
