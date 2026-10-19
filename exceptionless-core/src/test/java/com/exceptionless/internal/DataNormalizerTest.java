package com.exceptionless.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.json.JsonWriter;
import com.exceptionless.models.UserInfo;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class DataNormalizerTest {
  private static final Predicate<String> NONE = name -> false;

  public record Address(String street, String city) {}

  public record Customer(String name, Address address, List<String> tags, String password) {}

  public static class Order {
    private final String id;
    private final BigDecimal total;
    private final Customer customer;
    public final int quantity = 3;

    public Order(String id, BigDecimal total, Customer customer) {
      this.id = id;
      this.total = total;
      this.customer = customer;
    }

    public String getId() {
      return id;
    }

    public BigDecimal getTotal() {
      return total;
    }

    public Customer getCustomer() {
      return customer;
    }

    public boolean isPaid() {
      return true;
    }

    public String getBroken() {
      throw new IllegalStateException("getter throws");
    }

    public static String getStaticValue() {
      return "static";
    }
  }

  public static class Node {
    public Node next;
    public String name = "node";
  }

  private static class Hidden {
    @Override
    public String toString() {
      return "hidden!";
    }
  }

  @Test
  void keepsSimpleValues() {
    assertThat(DataNormalizer.normalize("s", 5, NONE)).isEqualTo("s");
    assertThat(DataNormalizer.normalize(42, 5, NONE)).isEqualTo(42);
    assertThat(DataNormalizer.normalize(true, 5, NONE)).isEqualTo(true);
    assertThat(DataNormalizer.normalize(Thread.State.RUNNABLE, 5, NONE)).isEqualTo("RUNNABLE");
    assertThat(DataNormalizer.normalize(URI.create("https://x"), 5, NONE)).isEqualTo("https://x");
    assertThat(
            DataNormalizer.normalize(
                UUID.fromString("00000000-0000-0000-0000-000000000002"), 5, NONE))
        .isEqualTo("00000000-0000-0000-0000-000000000002");
    assertThat(DataNormalizer.normalize(Instant.parse("2026-01-01T00:00:00Z"), 5, NONE))
        .isEqualTo("2026-01-01T00:00:00Z");
    assertThat(DataNormalizer.normalize(String.class, 5, NONE)).isEqualTo("java.lang.String");
    assertThat(DataNormalizer.normalize(Optional.of("x"), 5, NONE)).isEqualTo("x");
    assertThat(DataNormalizer.normalize(new byte[10], 5, NONE)).isEqualTo("[10 bytes]");
  }

  @Test
  void convertsRecordsAndBeans() {
    Order order =
        new Order(
            "o-1",
            new BigDecimal("9.99"),
            new Customer("Ann", new Address("Main", "Dallas"), List.of("vip"), "secret"));
    Object normalized = DataNormalizer.normalize(order, 5, NONE);
    assertThat(normalized).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) normalized;
    assertThat(map)
        .containsEntry("id", "o-1")
        .containsEntry("total", new BigDecimal("9.99"))
        .containsEntry("paid", true)
        .containsEntry("quantity", 3)
        .doesNotContainKeys("broken", "staticValue", "class");
    assertThat(map.get("customer"))
        .isEqualTo(
            Map.of(
                "name",
                "Ann",
                "address",
                Map.of("street", "Main", "city", "Dallas"),
                "tags",
                List.of("vip"),
                "password",
                "secret"));
  }

  @Test
  void appliesExclusionsAtEveryLevel() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("password", "x");
    input.put("nested", Map.of("Password", "y", "keep", 1));
    input.put("customer", new Customer("Ann", null, List.of(), "secret"));
    Object normalized = DataNormalizer.normalize(input, 5, Exclusions.of(List.of("*password*")));
    assertThat(JsonWriter.toJson(normalized))
        .doesNotContainIgnoringCase("password")
        .contains("\"keep\":1")
        .contains("\"name\":\"Ann\"");
  }

  @Test
  void limitsDepth() {
    Map<String, Object> deep = Map.of("a", Map.of("b", Map.of("c", Map.of("d", "leaf"))));
    assertThat(DataNormalizer.normalize(deep, 3, NONE))
        .isEqualTo(Map.of("a", Map.of("b", Map.of())));
    assertThat(DataNormalizer.normalize(deep, 1, NONE)).isEqualTo(Map.of());
    assertThat(DataNormalizer.normalize(deep, 0, NONE)).isNull();
    assertThat(DataNormalizer.normalize("simple values ignore depth", 0, NONE))
        .isEqualTo("simple values ignore depth");
  }

  @Test
  void handlesCircularReferences() {
    Node a = new Node();
    Node b = new Node();
    a.next = b;
    b.next = a;
    Object normalized = DataNormalizer.normalize(a, 10, NONE);
    assertThat(normalized).isEqualTo(Map.of("name", "node", "next", Map.of("name", "node")));

    List<Object> list = new ArrayList<>();
    list.add(list);
    assertThat(DataNormalizer.normalize(list, 10, NONE))
        .isEqualTo(java.util.Collections.singletonList(null));
  }

  @Test
  void sharedButNotCircularReferencesAreKept() {
    Address address = new Address("Main", "Dallas");
    Object normalized = DataNormalizer.normalize(List.of(address, address), 5, NONE);
    assertThat(normalized)
        .isEqualTo(
            List.of(
                Map.of("street", "Main", "city", "Dallas"),
                Map.of("street", "Main", "city", "Dallas")));
  }

  static class PackagePrivateException extends RuntimeException {
    public String getOrderId() {
      return "o-9";
    }
  }

  @Test
  void readsGettersOfNonPublicClasses() {
    assertThat(
            DataNormalizer.normalizeProperties(
                new PackagePrivateException(), 3, name -> !name.equals("orderId")))
        .containsEntry("orderId", "o-9");
  }

  @Test
  void usesToStringForInaccessibleTypes() {
    assertThat(DataNormalizer.normalize(new Hidden(), 5, NONE)).isEqualTo("hidden!");
  }

  @Test
  void summarizesThrowables() {
    assertThat(DataNormalizer.normalize(new IllegalStateException("bad"), 5, NONE))
        .isEqualTo(Map.of("type", "java.lang.IllegalStateException", "message", "bad"));
  }

  @Test
  void keepsSerializableModels() {
    UserInfo user = new UserInfo("id", "name");
    assertThat(DataNormalizer.normalize(user, 5, NONE)).isSameAs(user);
  }

  @Test
  void capsCollectionSizes() {
    List<Integer> big = new ArrayList<>();
    for (int i = 0; i < 5000; i++) {
      big.add(i);
    }
    assertThat((List<?>) DataNormalizer.normalize(big, 5, NONE)).hasSize(1000);
    Map<Integer, Integer> bigMap = new HashMap<>();
    for (int i = 0; i < 5000; i++) {
      bigMap.put(i, i);
    }
    assertThat((Map<?, ?>) DataNormalizer.normalize(bigMap, 5, NONE)).hasSize(1000);
  }

  @Test
  void convertsSetsAndArrays() {
    assertThat(DataNormalizer.normalize(Set.of("a"), 5, NONE)).isEqualTo(List.of("a"));
    assertThat(DataNormalizer.normalize(new String[] {"a", "b"}, 5, NONE))
        .isEqualTo(List.of("a", "b"));
    assertThat(DataNormalizer.normalize(new int[] {1, 2}, 5, NONE)).isEqualTo(List.of(1, 2));
  }

  @Test
  void normalizePropertiesReadsThrowableGetters() {
    Map<String, Object> props =
        DataNormalizer.normalizeProperties(
            new CustomException("m", 42),
            3,
            name ->
                name.equals("stackTrace")
                    || name.equals("cause")
                    || name.equals("message")
                    || name.equals("localizedMessage")
                    || name.equals("suppressed"));
    assertThat(props).containsEntry("errorCode", 42);
  }

  public static class CustomException extends RuntimeException {
    private final int errorCode;

    public CustomException(String message, int errorCode) {
      super(message);
      this.errorCode = errorCode;
    }

    public int getErrorCode() {
      return errorCode;
    }
  }
}
