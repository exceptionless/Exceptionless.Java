package com.exceptionless.internal;

import com.exceptionless.models.ErrorInfo;
import com.exceptionless.models.KnownDataKeys;
import com.exceptionless.models.ModuleInfo;
import com.exceptionless.models.StackFrame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Converts a {@link Throwable} (and its causes) into the Exceptionless error model. */
public final class ErrorConverter {
  /** Maximum number of causes followed. */
  static final int MAX_CAUSES = 20;

  /** Maximum frames kept per error. The top of the stack is kept; it decides stacking. */
  static final int MAX_FRAMES = 256;

  /** Maximum suppressed exceptions recorded per error. */
  static final int MAX_SUPPRESSED = 10;

  private static final int EXTENDED_DATA_DEPTH = 3;
  private static final Set<String> IGNORED_PROPERTIES =
      Set.of(
          "message",
          "localizedMessage",
          "cause",
          "stackTrace",
          "suppressed",
          "class",
          "ourStackTrace",
          "rootCause",
          "mostSpecificCause");

  private static final Pattern LAMBDA =
      Pattern.compile("\\$\\$Lambda(?:\\$\\d+)?(?:/0x[0-9a-fA-F]+)?");
  private static final Pattern CGLIB =
      Pattern.compile(
          "\\$\\$(EnhancerBySpringCGLIB|FastClassBySpringCGLIB|SpringCGLIB|EnhancerByCGLIB|FastClassByCGLIB)\\$\\$[0-9a-fA-F]+");
  private static final Pattern HIBERNATE_PROXY = Pattern.compile("\\$HibernateProxy\\$\\w+");
  private static final Pattern JDK_PROXY = Pattern.compile("^\\$Proxy\\d+$");
  private static final Pattern JDK_PROXY_PACKAGE = Pattern.compile("^jdk\\.proxy\\d+$");
  private static final Pattern GENERATED_ACCESSOR =
      Pattern.compile(
          "^(GeneratedMethodAccessor|GeneratedConstructorAccessor|GeneratedSerializationConstructorAccessor)\\d+$");

  private ErrorConverter() {}

  /**
   * Converts {@code throwable}.
   *
   * @param excluded data exclusion predicate applied to extended exception properties
   * @param modules modules to attach to the outermost error (may be empty)
   */
  public static ErrorInfo convert(
      Throwable throwable, Predicate<String> excluded, List<ModuleInfo> modules) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    ErrorInfo root = convertChain(throwable, null, excluded, seen, 0);
    root.getModules().addAll(modules);
    return root;
  }

  private static ErrorInfo convertChain(
      Throwable throwable,
      StackTraceElement @Nullable [] enclosingTrace,
      Predicate<String> excluded,
      Set<Throwable> seen,
      int level) {
    seen.add(throwable);
    ErrorInfo error = new ErrorInfo();
    error.setType(throwable.getClass().getName());
    error.setMessage(safeMessage(throwable));

    StackTraceElement[] trace = safeStackTrace(throwable);
    addFrames(error, trace, enclosingTrace);
    addExtendedData(error, throwable, excluded);
    addSuppressed(error, throwable, trace, excluded, seen, level);

    Throwable cause = safeCause(throwable);
    if (cause != null && level + 1 < MAX_CAUSES && !seen.contains(cause)) {
      error.setInner(convertChain(cause, trace, excluded, seen, level + 1));
    }
    return error;
  }

  private static void addFrames(
      ErrorInfo error, StackTraceElement[] trace, StackTraceElement @Nullable [] enclosing) {
    int end = trace.length;
    if (enclosing != null) {
      // Like Throwable.printStackTrace: drop the frames this cause shares with its enclosing trace.
      int m = trace.length - 1;
      int n = enclosing.length - 1;
      while (m >= 0 && n >= 0 && trace[m].equals(enclosing[n])) {
        m--;
        n--;
      }
      // Always keep the top frame so every error in the chain has a location.
      end = Math.max(m + 1, Math.min(1, trace.length));
    }
    int limit = Math.min(end, MAX_FRAMES);
    for (int i = 0; i < limit; i++) {
      error.getStackTrace().add(toFrame(trace[i]));
    }
    if (end > limit) {
      error.getData().put("frames_omitted", end - limit);
    }
  }

  /** Converts a single stack trace element. */
  public static StackFrame toFrame(StackTraceElement element) {
    StackFrame frame = new StackFrame();
    String className = element.getClassName();
    int lastDot = className.lastIndexOf('.');
    String namespace = lastDot > 0 ? className.substring(0, lastDot) : null;
    String type = lastDot > 0 ? className.substring(lastDot + 1) : className;
    if (namespace != null && JDK_PROXY_PACKAGE.matcher(namespace).matches()) {
      namespace = "jdk.proxy";
    }
    frame.setDeclaringNamespace(namespace);
    frame.setDeclaringType(normalizeTypeName(type));
    frame.setName(element.getMethodName());
    frame.setFileName(element.getFileName());
    if (element.getLineNumber() > 0) {
      frame.setLineNumber(element.getLineNumber());
    }
    if (element.isNativeMethod()) {
      frame.getData().put("is_native", true);
    }
    return frame;
  }

  /**
   * Removes the unstable parts of generated class names (lambdas, CGLIB/Hibernate/JDK proxies) so
   * the same code always produces the same stack signature.
   */
  static String normalizeTypeName(String type) {
    String result = LAMBDA.matcher(type).replaceAll("\\$\\$Lambda");
    result = CGLIB.matcher(result).replaceAll("\\$\\$$1");
    result = HIBERNATE_PROXY.matcher(result).replaceAll("\\$HibernateProxy");
    result = JDK_PROXY.matcher(result).replaceAll("\\$Proxy");
    result = GENERATED_ACCESSOR.matcher(result).replaceAll("$1");
    return result;
  }

  private static void addExtendedData(
      ErrorInfo error, Throwable throwable, Predicate<String> excluded) {
    try {
      Map<String, Object> properties =
          DataNormalizer.normalizeProperties(
              throwable,
              EXTENDED_DATA_DEPTH,
              name -> IGNORED_PROPERTIES.contains(name) || excluded.test(name));
      if (!properties.isEmpty()) {
        error.getData().put(KnownDataKeys.EXTENDED_DATA, properties);
      }
    } catch (RuntimeException | LinkageError e) {
      // Extended data is best effort and must never prevent the error from being reported.
    }
  }

  private static void addSuppressed(
      ErrorInfo error,
      Throwable throwable,
      StackTraceElement[] trace,
      Predicate<String> excluded,
      Set<Throwable> seen,
      int level) {
    Throwable[] suppressed;
    try {
      suppressed = throwable.getSuppressed();
    } catch (RuntimeException e) {
      return;
    }
    if (suppressed.length == 0 || level + 1 >= MAX_CAUSES) {
      return;
    }
    List<ErrorInfo> list = new ArrayList<>();
    for (Throwable item : suppressed) {
      if (list.size() >= MAX_SUPPRESSED) {
        break;
      }
      if (item != null && !seen.contains(item)) {
        list.add(convertChain(item, trace, excluded, seen, level + 1));
      }
    }
    if (!list.isEmpty()) {
      error.getData().put("suppressed", list);
    }
  }

  private static @Nullable String safeMessage(Throwable throwable) {
    try {
      return throwable.getMessage();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static StackTraceElement[] safeStackTrace(Throwable throwable) {
    try {
      StackTraceElement[] trace = throwable.getStackTrace();
      return trace != null ? trace : new StackTraceElement[0];
    } catch (RuntimeException e) {
      return new StackTraceElement[0];
    }
  }

  private static @Nullable Throwable safeCause(Throwable throwable) {
    try {
      return throwable.getCause();
    } catch (RuntimeException e) {
      return null;
    }
  }
}
