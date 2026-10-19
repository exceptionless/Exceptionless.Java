package com.exceptionless.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServletRequestInfoFactoryTest {
  @Test
  void parsesQueryStrings() {
    assertThat(ServletRequestInfoFactory.parseQueryString(null)).isEmpty();
    assertThat(ServletRequestInfoFactory.parseQueryString("")).isEmpty();
    assertThat(ServletRequestInfoFactory.parseQueryString("a=1&b=two+words&a=2&&c&=x&d=%E2%9C%93"))
        .containsExactly(
            Map.entry("a", "1"),
            Map.entry("b", "two words"),
            Map.entry("c", ""),
            Map.entry("d", "✓"));
  }

  @Test
  void keepsInvalidEscapesRaw() {
    assertThat(ServletRequestInfoFactory.parseQueryString("bad=%zz&ok=%41"))
        .containsExactly(Map.entry("bad", "%zz"), Map.entry("ok", "A"));
  }

  @Test
  void unwrapsNestedServletExceptions() {
    IllegalStateException root = new IllegalStateException("root");
    ServletException wrapped = new ServletException("outer", new ServletException("inner", root));
    assertThat(ExceptionlessFilter.unwrap(wrapped)).isSameAs(root);

    ServletException withoutCause = new ServletException("alone");
    assertThat(ExceptionlessFilter.unwrap(withoutCause)).isSameAs(withoutCause);

    RuntimeException other = new RuntimeException("x", root);
    assertThat(ExceptionlessFilter.unwrap(other)).isSameAs(other);
  }
}
