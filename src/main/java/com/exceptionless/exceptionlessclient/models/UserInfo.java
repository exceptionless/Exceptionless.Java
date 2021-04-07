package com.exceptionless.exceptionlessclient.models;

import com.exceptionless.exceptionlessclient.models.base.Model;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Value
@NonFinal
@EqualsAndHashCode(callSuper = true)
public class UserInfo extends Model {
  private String identity;
  private String name;
}
