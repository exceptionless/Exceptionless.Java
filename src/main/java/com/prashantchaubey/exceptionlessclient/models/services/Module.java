package com.prashantchaubey.exceptionlessclient.models.services;

import com.prashantchaubey.exceptionlessclient.models.base.Model;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@SuperBuilder
@Getter
public class Module extends Model {
    private long moduleId;
    private String name;
    private String version;
    private boolean isEntry;
    private LocalDate createdDate;
    private LocalDate modifiedDate;
}
