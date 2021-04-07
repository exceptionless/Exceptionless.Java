package com.exceptionless.exceptionlessclient.models.services;

import com.exceptionless.exceptionlessclient.models.base.Model;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@SuperBuilder
@Value
@NonFinal
@EqualsAndHashCode(callSuper = true)
public class Module extends Model {
    private long moduleId;
    private String name;
    private String version;
    private boolean isEntry;
    private LocalDate createdDate;
    private LocalDate modifiedDate;
}
