package com.exceptionless.exceptionlessclient.services;

import com.exceptionless.exceptionlessclient.models.services.Module;

import java.util.List;

public interface ModuleCollectorIF {
    List<Module> getModules();
}
