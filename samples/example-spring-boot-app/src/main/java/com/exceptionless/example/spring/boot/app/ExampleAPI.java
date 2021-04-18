package com.exceptionless.example.spring.boot.app;

import com.exceptionless.exceptionlessclient.ExceptionlessClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ExampleAPI {
  public static void main(String[] args) {
    SpringApplication.run(ExampleAPI.class, args);
  }

  @Bean
  public ExceptionlessClient exceptionlessClient() {
    return ExceptionlessClient.from(
        System.getenv("EXCEPTIONLESS_SAMPLE_APP_API_KEY"),
        System.getenv("EXCEPTIONLESS_SAMPLE_APP_SERVER_URL"));
  }
}
