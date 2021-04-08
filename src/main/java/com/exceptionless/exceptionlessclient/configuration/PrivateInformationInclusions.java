package com.exceptionless.exceptionlessclient.configuration;

import lombok.Builder;
import lombok.Getter;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class PrivateInformationInclusions {
  @Getter private Boolean queryString;
  @Getter private Boolean postData;
  @Getter private Boolean cookies;
  @Getter private Boolean ipAddress;
  @Getter private Boolean machineName;
  @Getter private Boolean userName;
  private final PropertyChangeSupport propertyChangeSupport;

  @Builder
  public PrivateInformationInclusions(
      Boolean queryString,
      Boolean postData,
      Boolean cookies,
      Boolean ipAddress,
      Boolean machineName,
      Boolean userName) {
    this.queryString = queryString == null || queryString;
    this.postData = postData == null || postData;
    this.cookies = cookies == null || cookies;
    this.ipAddress = ipAddress == null || ipAddress;
    this.machineName = machineName == null || machineName;
    this.userName = userName == null || userName;
    this.propertyChangeSupport = new PropertyChangeSupport(this);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    propertyChangeSupport.addPropertyChangeListener(listener);
  }

  public Boolean isAllIncluded() {
    return queryString && postData && cookies && ipAddress && machineName && userName;
  }

  public void applyToAll(Boolean include) {
    Boolean prevValue = isAllIncluded();
    queryString = postData = cookies = ipAddress = machineName = userName = include;
    propertyChangeSupport.firePropertyChange("all", prevValue, include);
  }

  public void setQueryString(Boolean queryString) {
    Boolean prevValue = this.queryString;
    this.queryString = queryString;
    propertyChangeSupport.firePropertyChange("querString", prevValue, queryString);
  }

  public void setPostData(Boolean postData) {
    Boolean prevValue = this.postData;
    this.postData = postData;
    propertyChangeSupport.firePropertyChange("postData", prevValue, queryString);
  }

  public void setCookies(Boolean cookies) {
    Boolean prevValue = this.cookies;
    this.cookies = cookies;
    propertyChangeSupport.firePropertyChange("cookies", prevValue, queryString);
  }

  public void setIpAddress(Boolean ipAddress) {
    Boolean prevValue = this.ipAddress;
    this.ipAddress = ipAddress;
    propertyChangeSupport.firePropertyChange("ipAddress", prevValue, queryString);
  }

  public void setMachineName(Boolean machineName) {
    Boolean prevValue = this.machineName;
    this.machineName = machineName;
    propertyChangeSupport.firePropertyChange("machineName", prevValue, queryString);
  }

  public void setUserName(Boolean userName) {
    Boolean prevValue = this.userName;
    this.userName = userName;
    propertyChangeSupport.firePropertyChange("userName", prevValue, queryString);
  }
}
