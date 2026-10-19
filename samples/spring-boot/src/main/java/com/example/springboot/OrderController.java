package com.example.springboot;

import com.exceptionless.ExceptionlessClient;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {
  private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);

  private final ExceptionlessClient exceptionless;

  OrderController(ExceptionlessClient exceptionless) {
    this.exceptionless = exceptionless;
  }

  @GetMapping("/orders/{id}")
  Map<String, Object> order(@PathVariable long id) {
    MDC.put("orderId", Long.toString(id));
    try {
      if (id <= 0) {
        // Unhandled: reported with the request details by the starter.
        throw new IllegalArgumentException("Order id must be positive: " + id);
      }
      if (id > 1000) {
        // Logged warnings are sent as log events, with the MDC values as properties.
        LOG.warn("Order {} is archived", id);
      }
      exceptionless.createFeatureUsage("View Order").setProperty("orderId", id).submit();
      return Map.of("id", id, "status", "shipped");
    } finally {
      MDC.remove("orderId");
    }
  }

  @GetMapping("/orders/{id}/pay")
  Map<String, Object> pay(@PathVariable long id) {
    try {
      throw new IllegalStateException("Card declined");
    } catch (IllegalStateException e) {
      // Handled exceptions can be sent with extra context; the reference id can be shown to users.
      exceptionless.createException(e).addTags("payments").setProperty("orderId", id).submit();
      return Map.of(
          "error",
          "Payment failed",
          "reference",
          String.valueOf(exceptionless.getLastReferenceId()));
    }
  }
}
