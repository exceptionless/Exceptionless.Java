package com.prashantchaubey.exceptionlessclient.queue;

import com.prashantchaubey.exceptionlessclient.utils.JsonUtils;
import lombok.Builder;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

@Builder
@Getter
public class EventDataFilter {
  @Builder.Default private Set<String> exclusions = new HashSet<>();
  @Builder.Default private int maxDepth = 3;

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
      data = JsonUtils.JSON_MAPPER.convertValue(data, Map.class);
    }

    if (data instanceof List) {
      List<Object> dataList = (List<Object>) data;
      return dataList.stream().map(val -> filter(val, currDepth + 1)).collect(Collectors.toList());
    }

    Map<String, Object> dataMap = (Map<String, Object>) data;
    Map<String, Object> result = new HashMap<>();
    for (String key : dataMap.keySet()) {
      //todo check that wildcard match work with this or not
      if (exclusions.stream().anyMatch(key::matches)) {
        continue;
      }
      result.put(key, filter(dataMap.get(key), currDepth + 1));
    }

    return result;
  }
}
