package com.exceptionless.exceptionlessclient.models.error;

import com.exceptionless.exceptionlessclient.models.Module;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
public class Error extends InnerError {
  @Builder.Default private List<Module> modules = new ArrayList<>();
}
