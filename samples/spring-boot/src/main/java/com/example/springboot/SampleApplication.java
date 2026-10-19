package com.example.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A Spring Boot app wired to Exceptionless by {@code exceptionless-spring-boot-starter}.
 *
 * <pre>
 * EXCEPTIONLESS_API_KEY=your-key ./mvnw -pl samples/spring-boot -am spring-boot:run
 * curl localhost:8080/orders/42        # logs + feature usage
 * curl localhost:8080/orders/0         # unhandled exception, reported with the request
 * curl localhost:8080/does-not-exist   # 404
 * </pre>
 */
@SpringBootApplication
public class SampleApplication {
  public static void main(String[] args) {
    SpringApplication.run(SampleApplication.class, args);
  }
}
