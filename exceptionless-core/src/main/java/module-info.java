/** The Exceptionless client for Java. */
module com.exceptionless.core {
  requires transitive java.net.http;
  requires static java.management;
  requires static jdk.management;
  requires static transitive org.jspecify;

  exports com.exceptionless;
  exports com.exceptionless.json;
  exports com.exceptionless.logging;
  exports com.exceptionless.models;
  exports com.exceptionless.plugins;
  exports com.exceptionless.queue;
  exports com.exceptionless.settings;
  exports com.exceptionless.storage;
  exports com.exceptionless.submission;
}
