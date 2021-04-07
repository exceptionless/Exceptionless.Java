package com.exceptionless.exceptionlessclient.models.base;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@NoArgsConstructor // So that `@Data` classes can extend this and we can call `super()` from normal
                   // `@Builder` classes
@Data
public abstract class Model {
  @Singular("property")
  protected Map<String, Object> data;
}
