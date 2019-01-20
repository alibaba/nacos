package com.alibaba.nacos.naming.consistency.ephemeral.partition;

import com.alibaba.nacos.api.naming.pojo.Instance;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author nkorange
 */
@Component
public class DataStore {

    private Map<String, List<Instance>> dataMap = new ConcurrentHashMap<>();

    public void put(String key, List<Instance> value) {
        dataMap.put(key, value);
    }

    public void remove(String key) {
        dataMap.remove(key);
    }

    public List<Instance> get(String key) {
        return dataMap.get(key);
    }

    public Map<String, List<Instance>> batchGet(List<String> keys) {
        Map<String, List<Instance>> map = new HashMap<>();
        for (String key : keys) {
            if (!dataMap.containsKey(key)) {
                continue;
            }
            map.put(key, dataMap.get(key));
        }
        return map;
    }
}
