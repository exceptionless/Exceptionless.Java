package com.exceptionless.spring.boot.mvc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class MvcTestApplication {
  @RestController
  static class TestController {
    // Not under com.exceptionless: the appender ignores the client's own loggers.
    private static final Logger LOG = LoggerFactory.getLogger("com.example.Orders");

    @GetMapping("/boom")
    String boom() {
      throw new IllegalStateException("controller boom");
    }

    @GetMapping("/logged")
    String logged() {
      LOG.error("Logged failure", new IllegalArgumentException("logged boom"));
      return "ok";
    }

    @GetMapping("/ok")
    String ok() {
      return "ok";
    }
  }
}
