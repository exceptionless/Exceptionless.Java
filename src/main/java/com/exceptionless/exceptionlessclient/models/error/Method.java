package com.exceptionless.exceptionlessclient.models.error;

import com.exceptionless.exceptionlessclient.models.base.Model;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@Value
@NonFinal
@EqualsAndHashCode(callSuper = true)
public class Method extends Model {
    List<String> genericArguments;
    List<Parameter> parameters;
    boolean isSignatureTarget;
    String declaringNamespace;
    String declaringType;
    String name;
    long moduleId;
}
