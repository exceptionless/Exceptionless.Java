package com.exceptionless.internal;

import com.exceptionless.json.JsonSerializable;
import java.io.File;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Converts arbitrary objects into a JSON-friendly tree of maps, lists and simple values, applying a
 * depth limit and property-name exclusions. The result can always be serialized.
 *
 * <p>Plain objects are read through their public getters and fields. Records are read through their
 * components. Anything that can't be read is converted with {@code toString()}. The readable
 * properties of each class are looked up once and cached.
 */
public final class DataNormalizer {
  /** Default depth used for custom properties. */
  public static final int DEFAULT_MAX_DEPTH = 5;

  private static final int MAX_ITEMS = 1000;
  private static final Log LOG = Log.get(DataNormalizer.class);

  /** The getters and public fields of a class, in the order they are read. */
  private static final ClassValue<List<Property>> PROPERTIES =
      new ClassValue<>() {
        @Override
        protected List<Property> computeValue(Class<?> type) {
          return properties(type);
        }
      };

  /** A readable property: a getter or a public instance field. */
  private record Property(String name, AccessibleObject member) {
    @Nullable Object read(Object target) throws ReflectiveOperationException {
      return member instanceof Method method ? method.invoke(target) : ((Field) member).get(target);
    }
  }

  private final int maxDepth;
  private final Predicate<String> excluded;
  private final Set<Object> visiting = Collections.newSetFromMap(new IdentityHashMap<>());

  private DataNormalizer(int maxDepth, Predicate<String> excluded) {
    this.maxDepth = maxDepth;
    this.excluded = excluded;
  }

  /**
   * Normalizes {@code value}.
   *
   * @param maxDepth how many levels of nested objects to keep; deeper objects are dropped
   * @param excluded returns true for property names that must not be included
   */
  public static @Nullable Object normalize(
      @Nullable Object value, int maxDepth, Predicate<String> excluded) {
    return new DataNormalizer(Math.max(0, maxDepth), excluded).convert(value, 0);
  }

  /**
   * Normalizes the public properties of {@code bean} into a map, even when it is a type (such as a
   * {@link Throwable}) that {@link #normalize} would otherwise summarize.
   */
  public static Map<String, Object> normalizeProperties(
      Object bean, int maxDepth, Predicate<String> excluded) {
    DataNormalizer normalizer = new DataNormalizer(Math.max(1, maxDepth), excluded);
    normalizer.visiting.add(bean);
    Object result = normalizer.convertBean(bean, 0);
    @SuppressWarnings("unchecked")
    Map<String, Object> map =
        result instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    return map;
  }

  /**
   * True when {@code value} is a single value with no nested properties to normalize. Except for
   * strings, numbers and booleans, these are converted with {@link #simpleValueToString}. {@link
   * com.exceptionless.json.JsonWriter} uses the same rules.
   */
  public static boolean isSimpleValue(@Nullable Object value) {
    return value == null
        || value instanceof CharSequence
        || value instanceof Number
        || value instanceof Boolean
        || value instanceof Character
        || value instanceof Enum<?>
        || value instanceof TemporalAccessor
        || value instanceof TemporalAmount
        || value instanceof Date
        || value instanceof UUID
        || value instanceof URI
        || value instanceof URL
        || value instanceof File
        || value instanceof Path
        || value instanceof Locale
        || value instanceof Currency
        || value instanceof TimeZone
        || value instanceof Charset
        || value instanceof Pattern
        || value instanceof Class<?>;
  }

  /**
   * Converts a {@link #isSimpleValue simple value} to the string sent to the server: enum names,
   * class names, ISO 8601 dates and otherwise {@code toString()}.
   */
  public static String simpleValueToString(Object value) {
    if (value instanceof Enum<?> enumValue) {
      return enumValue.name();
    }
    if (value instanceof Class<?> type) {
      return type.getName();
    }
    if (value instanceof OffsetDateTime dateTime) {
      return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime);
    }
    if (value instanceof Date date) {
      return date.toInstant().toString();
    }
    return value.toString();
  }

  private @Nullable Object convert(@Nullable Object value, int depth) {
    if (value == null) {
      return null;
    }
    if (value instanceof CharSequence) {
      return value.toString();
    }
    if (value instanceof Number || value instanceof Boolean || value instanceof JsonSerializable) {
      return value;
    }
    if (isSimpleValue(value)) {
      return simpleValueToString(value);
    }
    if (value instanceof Optional<?> optional) {
      return convert(optional.orElse(null), depth);
    }
    if (value instanceof byte[] bytes) {
      return "[" + bytes.length + " bytes]";
    }
    if (depth >= maxDepth) {
      return null;
    }
    if (!visiting.add(value)) {
      return null; // circular reference
    }
    try {
      if (value instanceof Map<?, ?> map) {
        return convertMap(map, depth);
      }
      if (value instanceof Iterable<?> iterable) {
        List<@Nullable Object> list = new ArrayList<>();
        for (Object item : iterable) {
          if (list.size() >= MAX_ITEMS) {
            break;
          }
          list.add(convert(item, depth + 1));
        }
        return list;
      }
      if (value.getClass().isArray()) {
        int length = Math.min(Array.getLength(value), MAX_ITEMS);
        List<@Nullable Object> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
          list.add(convert(Array.get(value, i), depth + 1));
        }
        return list;
      }
      if (value instanceof Throwable throwable) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", throwable.getClass().getName());
        if (throwable.getMessage() != null) {
          map.put("message", throwable.getMessage());
        }
        return map;
      }
      if (value.getClass().isRecord()) {
        return convertRecord(value, depth);
      }
      return convertBean(value, depth);
    } finally {
      visiting.remove(value);
    }
  }

  private Map<String, Object> convertMap(Map<?, ?> map, int depth) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (result.size() >= MAX_ITEMS) {
        break;
      }
      String key = String.valueOf(entry.getKey());
      if (excluded.test(key)) {
        continue;
      }
      Object converted = convert(entry.getValue(), depth + 1);
      if (converted != null) {
        result.put(key, converted);
      }
    }
    return result;
  }

  private Object convertRecord(Object value, int depth) {
    Map<String, Object> result = new LinkedHashMap<>();
    try {
      for (RecordComponent component : value.getClass().getRecordComponents()) {
        String name = component.getName();
        if (excluded.test(name)) {
          continue;
        }
        Method accessor = component.getAccessor();
        if (!accessor.canAccess(value)) {
          accessor.setAccessible(true);
        }
        put(result, name, accessor.invoke(value), depth);
      }
      return result;
    } catch (ReflectiveOperationException | RuntimeException e) {
      LOG.debug(() -> "Unable to read record " + value.getClass().getName() + ": " + e);
      return safeToString(value);
    }
  }

  private Object convertBean(Object value, int depth) {
    Class<?> type = value.getClass();
    List<Property> properties = PROPERTIES.get(type);
    if (properties.isEmpty()) {
      return safeToString(value);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    for (Property property : properties) {
      String name = property.name();
      if (excluded.test(name) || result.containsKey(name)) {
        continue;
      }
      if (!accessible(property.member(), value)) {
        continue;
      }
      try {
        put(result, name, property.read(value), depth);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
        LOG.trace(() -> "Unable to read property " + name + " of " + type.getName() + ": " + e);
      }
    }
    return result;
  }

  /** The getters, then the public instance fields, of {@code type}. */
  private static List<Property> properties(Class<?> type) {
    List<Property> properties = new ArrayList<>();
    try {
      for (Method method : type.getMethods()) {
        String name = propertyName(method);
        if (name != null) {
          properties.add(new Property(name, method));
        }
      }
      for (Field field : type.getFields()) {
        if (!Modifier.isStatic(field.getModifiers())) {
          properties.add(new Property(field.getName(), field));
        }
      }
    } catch (RuntimeException | LinkageError e) {
      LOG.debug(() -> "Unable to read the properties of " + type.getName() + ": " + e);
    }
    return List.copyOf(properties);
  }

  /**
   * Public members of non-public classes (such as package-private exception types) need {@code
   * setAccessible}, which only works when the package is open to this library (always the case on
   * the class path).
   */
  private static boolean accessible(AccessibleObject member, Object target) {
    try {
      return member.canAccess(target) || member.trySetAccessible();
    } catch (RuntimeException e) {
      return false;
    }
  }

  private void put(Map<String, Object> result, String name, @Nullable Object raw, int depth) {
    Object converted = convert(raw, depth + 1);
    if (converted != null) {
      result.put(name, converted);
    }
  }

  private static @Nullable String propertyName(Method method) {
    if (method.getParameterCount() != 0
        || Modifier.isStatic(method.getModifiers())
        || method.getReturnType() == void.class
        || method.getDeclaringClass() == Object.class
        || method.isBridge()
        || method.isSynthetic()) {
      return null;
    }
    String name = method.getName();
    if (name.startsWith("get") && name.length() > 3) {
      return decapitalize(name.substring(3));
    }
    if (name.startsWith("is")
        && name.length() > 2
        && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
      return decapitalize(name.substring(2));
    }
    return null;
  }

  private static String decapitalize(String name) {
    if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
      return name; // e.g. getURL -> URL
    }
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  private static String safeToString(Object value) {
    try {
      return String.valueOf(value);
    } catch (RuntimeException e) {
      return value.getClass().getName();
    }
  }
}
