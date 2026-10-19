package com.exceptionless.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.json.JsonWriter;
import com.exceptionless.models.ErrorInfo;
import com.exceptionless.models.KnownDataKeys;
import com.exceptionless.models.ModuleInfo;
import com.exceptionless.models.StackFrame;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErrorConverterTest {
  public static class PaymentException extends RuntimeException {
    private final String orderId;
    private final String password = "hunter2";

    PaymentException(String message, String orderId, Throwable cause) {
      super(message, cause);
      this.orderId = orderId;
    }

    public String getOrderId() {
      return orderId;
    }

    public String getPassword() {
      return password;
    }
  }

  private static ErrorInfo convert(Throwable t) {
    return ErrorConverter.convert(t, Exclusions.of(List.of("*password*")), List.of());
  }

  @Test
  void mapsTypeMessageAndFrames() {
    ErrorInfo error = convert(new IllegalArgumentException("bad input"));
    assertThat(error.getType()).isEqualTo("java.lang.IllegalArgumentException");
    assertThat(error.getMessage()).isEqualTo("bad input");
    StackFrame top = error.getStackTrace().get(0);
    assertThat(top.getDeclaringNamespace()).isEqualTo("com.exceptionless.internal");
    assertThat(top.getDeclaringType()).isEqualTo("ErrorConverterTest");
    assertThat(top.getName()).isEqualTo("mapsTypeMessageAndFrames");
    assertThat(top.getFileName()).isEqualTo("ErrorConverterTest.java");
    assertThat(top.getLineNumber()).isPositive();
  }

  @Test
  void followsTheCauseChain() {
    IOException root = new IOException("disk full");
    UncheckedIOException middle = new UncheckedIOException("write failed", root);
    IllegalStateException outer = new IllegalStateException("save failed", middle);
    ErrorInfo error = convert(outer);
    assertThat(error.getType()).isEqualTo("java.lang.IllegalStateException");
    assertThat(error.getInner().getType()).isEqualTo("java.io.UncheckedIOException");
    assertThat(error.getInner().getInner().getType()).isEqualTo("java.io.IOException");
    assertThat(error.getRootCause().getMessage()).isEqualTo("disk full");
    // Causes keep at least their top frame even when every frame is shared with the parent.
    assertThat(error.getInner().getStackTrace()).isNotEmpty();
    assertThat(error.getInner().getInner().getStackTrace()).isNotEmpty();
  }

  @Test
  void trimsFramesSharedWithTheEnclosingTrace() {
    Exception cause = deeper(() -> new Exception("deep"));
    RuntimeException outer = new RuntimeException("outer", cause);
    ErrorInfo error = convert(outer);
    List<StackFrame> causeFrames = error.getInner().getStackTrace();
    assertThat(causeFrames).extracting(StackFrame::getName).contains("deeper");
    assertThat(causeFrames.size()).isLessThan(outer.getCause().getStackTrace().length);
  }

  private static Exception deeper(Supplier<Exception> supplier) {
    return supplier.get();
  }

  @Test
  void stopsAtCircularCauses() {
    RuntimeException a = new RuntimeException("a");
    RuntimeException b = new RuntimeException("b", a);
    a.initCause(b);
    ErrorInfo error = convert(a);
    assertThat(error.getInner().getMessage()).isEqualTo("b");
    assertThat(error.getInner().getInner()).isNull();
  }

  @Test
  void limitsCauseDepth() {
    Throwable t = new RuntimeException("0");
    for (int i = 1; i < 50; i++) {
      t = new RuntimeException(Integer.toString(i), t);
    }
    ErrorInfo error = convert(t);
    int depth = 0;
    while (error != null) {
      depth++;
      error = error.getInner();
    }
    assertThat(depth).isEqualTo(ErrorConverter.MAX_CAUSES);
  }

  @Test
  void recordsSuppressedExceptions() {
    Exception primary = new Exception("primary");
    primary.addSuppressed(new IllegalStateException("close failed"));
    ErrorInfo error = convert(primary);
    @SuppressWarnings("unchecked")
    List<ErrorInfo> suppressed = (List<ErrorInfo>) error.getData().get("suppressed");
    assertThat(suppressed)
        .singleElement()
        .satisfies(s -> assertThat(s.getMessage()).isEqualTo("close failed"));
  }

  @Test
  void addsCustomPropertiesAsExtendedDataWithExclusions() {
    ErrorInfo error = convert(new PaymentException("declined", "order-7", null));
    @SuppressWarnings("unchecked")
    Map<String, Object> ext =
        (Map<String, Object>) error.getData().get(KnownDataKeys.EXTENDED_DATA);
    assertThat(ext)
        .containsEntry("orderId", "order-7")
        .doesNotContainKeys(
            "password", "stackTrace", "message", "cause", "localizedMessage", "suppressed");
  }

  @Test
  void plainExceptionsHaveNoExtendedData() {
    assertThat(convert(new RuntimeException("x")).getData())
        .doesNotContainKey(KnownDataKeys.EXTENDED_DATA);
  }

  @Test
  void handlesThrowablesWithBrokenAccessors() {
    RuntimeException broken =
        new RuntimeException() {
          @Override
          public String getMessage() {
            throw new IllegalStateException("nope");
          }

          @Override
          public StackTraceElement[] getStackTrace() {
            throw new IllegalStateException("nope");
          }
        };
    ErrorInfo error = convert(broken);
    assertThat(error.getType()).startsWith("com.exceptionless.internal.ErrorConverterTest$");
    assertThat(error.getStackTrace()).isEmpty();
  }

  @Test
  void capsFramesAndRecordsOmittedCount() {
    StackOverflowError overflow = recurse(0);
    ErrorInfo error = convert(overflow);
    assertThat(error.getStackTrace()).hasSize(ErrorConverter.MAX_FRAMES);
    assertThat((Integer) error.getData().get("frames_omitted")).isPositive();
  }

  private static StackOverflowError recurse(int depth) {
    try {
      return recurse(depth + 1);
    } catch (StackOverflowError e) {
      return e;
    }
  }

  @Test
  void attachesModulesToTheOutermostErrorOnly() {
    ModuleInfo module = new ModuleInfo().setName("lib").setVersion("1.0");
    ErrorInfo error =
        ErrorConverter.convert(
            new RuntimeException("x", new RuntimeException("y")), name -> false, List.of(module));
    assertThat(error.getModules()).containsExactly(module);
    assertThat(error.getInner().getModules()).isEmpty();
  }

  @Test
  void marksNativeFrames() {
    StackFrame frame =
        ErrorConverter.toFrame(new StackTraceElement("java.lang.Thread", "sleep0", null, -2));
    assertThat(frame.getData()).containsEntry("is_native", true);
    assertThat(frame.getLineNumber()).isNull();
  }

  @Test
  void handlesClassesWithoutPackage() {
    StackFrame frame =
        ErrorConverter.toFrame(new StackTraceElement("Main", "main", "Main.java", 3));
    assertThat(frame.getDeclaringNamespace()).isNull();
    assertThat(frame.getDeclaringType()).isEqualTo("Main");
  }

  @ParameterizedTest
  @CsvSource({
    "Foo$$Lambda$123/0x0000000800c0b840, Foo$$Lambda",
    "Foo$$Lambda/0x000071b8c8003400, Foo$$Lambda",
    "Foo$$Lambda$14, Foo$$Lambda",
    "OrderService$$EnhancerBySpringCGLIB$$a1b2c3, OrderService$$EnhancerBySpringCGLIB",
    "OrderService$$SpringCGLIB$$0, OrderService$$SpringCGLIB",
    "Customer$HibernateProxy$AbC123, Customer$HibernateProxy",
    "$Proxy42, $Proxy",
    "GeneratedMethodAccessor17, GeneratedMethodAccessor",
    "Outer$Inner, Outer$Inner",
    "Plain, Plain",
  })
  void normalizesGeneratedClassNames(String input, String expected) {
    assertThat(ErrorConverter.normalizeTypeName(input)).isEqualTo(expected);
  }

  @Test
  void normalizesJdkProxyPackages() {
    StackFrame frame =
        ErrorConverter.toFrame(new StackTraceElement("jdk.proxy2.$Proxy12", "call", null, -1));
    assertThat(frame.getDeclaringNamespace()).isEqualTo("jdk.proxy");
    assertThat(frame.getDeclaringType()).isEqualTo("$Proxy");
  }

  @Test
  void lambdaFramesAreStableAcrossRuns() {
    Supplier<RuntimeException> a = () -> new RuntimeException("x");
    StackFrame frame = convert(a.get()).getStackTrace().get(0);
    assertThat(frame.getName()).startsWith("lambda$");
    assertThat(frame.getDeclaringType()).isEqualTo("ErrorConverterTest");
  }

  @Test
  void serializesToTheServerShape() {
    String json =
        JsonWriter.toJson(
            convert(new IllegalStateException("boom", new RuntimeException("cause"))));
    assertThat(json)
        .contains("\"type\":\"java.lang.IllegalStateException\"")
        .contains("\"stack_trace\":[")
        .contains("\"declaring_namespace\":\"com.exceptionless.internal\"")
        .contains("\"declaring_type\":\"ErrorConverterTest\"")
        .contains("\"file_name\":\"ErrorConverterTest.java\"")
        .contains("\"line_number\":")
        .contains("\"inner\":{");
  }
}
