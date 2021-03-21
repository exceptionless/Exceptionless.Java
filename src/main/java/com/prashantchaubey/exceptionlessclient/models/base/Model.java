package com.prashantchaubey.exceptionlessclient.models.base;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class Model {
  protected Map<String, Object> data;
}
