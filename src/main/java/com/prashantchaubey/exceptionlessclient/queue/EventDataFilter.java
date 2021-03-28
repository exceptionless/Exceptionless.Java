package com.prashantchaubey.exceptionlessclient.queue;

import com.prashantchaubey.exceptionlessclient.utils.Utils;
import lombok.Builder;

import java.util.*;
import java.util.stream.Collectors;

public class EventDataFilter {
  private Set<String> exclusions;
  private int maxDepth;

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
    if (data == null || currDepth >= maxDepth) {
      return data;
    }

    if (!(data instanceof Map || data instanceof List)) {
      data = Utils.JSON_MAPPER.convertValue(data, Map.class);
    }

    if (data instanceof List) {
      List<Object> dataList = (List<Object>) data;
      return dataList.stream().map(val -> filter(val, currDepth + 1)).collect(Collectors.toList());
    }

    return ((Map<String, Object>) data)
        .entrySet().stream()
            .filter(
                entry ->
                    exclusions.stream()
                        .anyMatch(
                            exclusion -> Utils.match(entry.getKey(), exclusion)))
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, entry -> filter(entry.getValue(), currDepth + 1)));
  }
}
