package com.exceptionless.example.spring.boot.app.api;

import com.exceptionless.exceptionlessclient.ExceptionlessClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class ExampleResource {
  private final ExceptionlessClient exceptionlessClient;

  @Autowired
  public ExampleResource(ExceptionlessClient exceptionlessClient) {
    this.exceptionlessClient = exceptionlessClient;
  }

  @PostMapping("/log")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void submitLog() {
    exceptionlessClient.submitLog("test-log");
  }
}
