package com.exceptionless.exceptionlessclient.models.error;

import com.exceptionless.exceptionlessclient.models.base.Model;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@Value
@EqualsAndHashCode(callSuper = true)
public class Parameter extends Model {
    List<String> genericArguments;
    String name;
    String type;
    String typeNamespace;
}
