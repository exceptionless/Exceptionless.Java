package com.prashantchaubey.exceptionlessclient.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Builder
@Getter
@Setter
public class DataExclusions {
  private boolean queryString;
  private boolean postData;
  private boolean cookies;
  private boolean ipAddress;
  private boolean machineName;
  private boolean userName;
  private Set<String> others;

  public void permitPrivateInformation() {
    queryString = postData = cookies = ipAddress = machineName = userName = false;
  }
}
