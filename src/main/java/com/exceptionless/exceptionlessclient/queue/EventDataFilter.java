package com.exceptionless.exceptionlessclient.queue;

import com.exceptionless.exceptionlessclient.utils.Utils;
import lombok.Builder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EventDataFilter {
  private final Set<String> exclusions;
  private final int maxDepth;

  @Builder
  public EventDataFilter(Set<String> exclusions, Integer maxDepth) {
    this.exclusions = exclusions == null ? new HashSet<>() : exclusions;
    this.maxDepth = maxDepth == null ? 3 : maxDepth;
  }

  public Object filter(Object data) {
    if (exclusions.isEmpty()) {
      return data;
    }

    return filter(data, 1);
  }

  private Object filter(Object data, int currDepth) {
    if (shouldNotFilter(data, currDepth)) {
      return data;
    }

    // Object other than JSON primitive
    if (!(data instanceof Map || data instanceof List)) {
      data = Utils.JSON_MAPPER.convertValue(data, Map.class);
    }

    if (data instanceof List) {
      List<Object> dataList = (List<Object>) data;
      return dataList.stream().map(val -> filter(val, currDepth)).collect(Collectors.toList());
    }

    return ((Map<String, Object>) data)
        .entrySet().stream()
            .filter(
                entry ->
                    entry.getValue() != null
                        && exclusions.stream()
                            .noneMatch(exclusion -> Utils.match(entry.getKey(), exclusion)))
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, entry -> filter(entry.getValue(), currDepth + 1)));
  }

  private boolean shouldNotFilter(Object data, int currentDepth) {
    return currentDepth > maxDepth
        || data == null
        || (data instanceof Number)
        || (data instanceof String)
        || (data instanceof Boolean);
  }
}
