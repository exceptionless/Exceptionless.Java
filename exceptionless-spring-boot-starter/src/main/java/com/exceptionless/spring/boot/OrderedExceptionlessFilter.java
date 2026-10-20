package com.exceptionless.spring.boot;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.servlet.ExceptionlessFilter;
import org.springframework.core.Ordered;

/**
 * The {@link ExceptionlessFilter} registered by the auto-configuration in servlet web applications.
 * Spring Boot registers it for all requests, in {@link #getOrder() order}.
 */
public class OrderedExceptionlessFilter extends ExceptionlessFilter implements Ordered {
  private final int order;

  public OrderedExceptionlessFilter(ExceptionlessClient client, int order) {
    super(client);
    this.order = order;
  }

  @Override
  public int getOrder() {
    return order;
  }
}
