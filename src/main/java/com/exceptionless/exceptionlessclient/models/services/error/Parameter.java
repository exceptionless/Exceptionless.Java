package com.exceptionless.exceptionlessclient.models.services.error;

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
public class Parameter extends Model {
    private List<String> genericArguments;
    private String name;
    private String type;
    private String typeNamespace;
}
