package com.prashantchaubey.exceptionlessclient.models.services.error;

import com.prashantchaubey.exceptionlessclient.models.base.Model;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@Getter
public class Method extends Model {
    private List<String> genericArguments;
    private List<Parameter> parameters;
    private boolean isSignatureTarget;
    private String declaringNamespace;
    private String declaringType;
    private String name;
    private long moduleId;
}
