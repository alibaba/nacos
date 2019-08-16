/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.client.utils.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 * ConfigChangeEvent
 *
 * @author rushsky518
 */
public class ConfigChangeEvent {
    private Map<String, ConfigChangeItem> result;

    public ConfigChangeEvent(String dataId, String oldContent, String content) throws IOException {
        init(dataId, oldContent, content);
    }

    public ConfigChangeItem getChangeItem(String key) {
        return result.get(key);
    }

    public Collection<ConfigChangeItem> getChangeItems() {
        return result.values();
    }

    private void init(String dataId, String oldContent, String content) throws IOException {
        result = new HashMap<String, ConfigChangeItem>(32);

        if (dataId.endsWith(PROPERTIES_SUFFIX)) {
            Properties oldProps = new Properties();
            Properties newProps = new Properties();

            if (!StringUtils.isBlank(oldContent)) {
                oldProps.load(new StringReader(oldContent));
            }
            if (!StringUtils.isBlank(content)) {
                newProps.load(new StringReader(content));
            }

            filterData(oldProps, newProps);
        } else if (dataId.endsWith(YML_SUFFIX) || dataId.endsWith(YAML_SUFFIX)) {
            Map<String, Object> oldMap = Collections.emptyMap();
            Map<String, Object> newMap = Collections.emptyMap();

            if (!StringUtils.isBlank(oldContent)) {
                oldMap =  (new Yaml()).load(oldContent);
                oldMap = getFlattenedMap(oldMap);
            }
            if (!StringUtils.isBlank(content)) {
                newMap = (new Yaml()).load(content);
                newMap = getFlattenedMap(newMap);
            }

            filterData(oldMap, newMap);
        }
    }

    private void filterData(Map oldMap, Map newMap) {
        for (Iterator<Map.Entry<String, Object>> entryItr = oldMap.entrySet().iterator(); entryItr.hasNext();) {
            Map.Entry<String, Object> e = entryItr.next();
            ConfigChangeItem cci = null;
            if (newMap.containsKey(e.getKey()))  {
                if (e.getValue().equals(newMap.get(e.getKey()))) {
                    continue;
                }
                cci = new ConfigChangeItem(e.getKey(), e.getValue().toString(), newMap.get(e.getKey()).toString());
                cci.setType(ConfigChangeItem.PropertyChangeType.MODIFIED);
            } else {
                cci = new ConfigChangeItem(e.getKey(), e.getValue().toString(), null);
                cci.setType(ConfigChangeItem.PropertyChangeType.DELETED);
            }

            result.put(e.getKey(), cci);
        }

        for (Iterator<Map.Entry<String, Object>> entryItr = newMap.entrySet().iterator(); entryItr.hasNext();) {
            Map.Entry<String, Object> e = entryItr.next();
            if (!oldMap.containsKey(e.getKey())) {
                ConfigChangeItem cci = new ConfigChangeItem(e.getKey(), null, e.getValue().toString());
                cci.setType(ConfigChangeItem.PropertyChangeType.ADDED);
                result.put(e.getKey(), cci);
            }
        }
    }

    private final Map<String, Object> getFlattenedMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<String, Object>(128);
        buildFlattenedMap(result, source, null);
        return result;
    }

    private void buildFlattenedMap(Map<String, Object> result, Map<String, Object> source, String path) {
        for (Iterator<Map.Entry<String, Object>> itr = source.entrySet().iterator(); itr.hasNext(); ) {
            Map.Entry<String, Object> e = itr.next();
            String key = e.getKey();
            if (StringUtils.isNotBlank(path)) {
                if (e.getKey().startsWith("[")) {
                    key = path + key;
                } else {
                    key = path + '.' + key;
                }
            }
            if (e.getValue() instanceof String) {
                result.put(key, e.getValue());
            } else if (e.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) e.getValue();
                buildFlattenedMap(result, map, key);
            } else if (e.getValue() instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<Object> collection = (Collection<Object>) e.getValue();
                if (collection.isEmpty()) {
                    result.put(key, "");
                } else {
                    int count = 0;
                    for (Object object : collection) {
                        buildFlattenedMap(result, Collections.singletonMap("[" + (count++) + "]", object), key);
                    }
                }
            } else {
                result.put(key, (e.getValue() != null ? e.getValue() : ""));
            }
        }
    }

    static final String PROPERTIES_SUFFIX = ".properties";
    static final String YAML_SUFFIX = ".ymal";
    static final String YML_SUFFIX = ".yml";
}

