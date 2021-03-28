package com.prashantchaubey.exceptionlessclient.models.services.error;

import com.prashantchaubey.exceptionlessclient.models.base.Model;
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
    private List<String> genericArguments;
    private List<Parameter> parameters;
    private boolean isSignatureTarget;
    private String declaringNamespace;
    private String declaringType;
    private String name;
    private long moduleId;
}
