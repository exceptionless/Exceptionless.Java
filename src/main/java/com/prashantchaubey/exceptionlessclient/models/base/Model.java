package com.prashantchaubey.exceptionlessclient.models.base;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@Getter
public class Model {
  protected Map<String, Object> data;
}
