package com.prashantchaubey.exceptionlessclient.models.services.error;

import com.prashantchaubey.exceptionlessclient.models.base.Model;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@Getter
public class Parameter extends Model {
    private List<String> genericArguments;
    private String name;
    private String type;
    private String typeNamespace;
}
