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

import com.alibaba.nacos.api.config.ConfigType;
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

    public ConfigChangeEvent(String dataId, String oldContent, String content) {
        init(dataId, oldContent, content);
    }

    public ConfigChangeItem getChangeItem(String key) {
        return result.get(key);
    }

    public Collection<ConfigChangeItem> getChangeItems() {
        return result.values();
    }

    private void init(String dataId, String oldContent, String content) {
        result = new HashMap<String, ConfigChangeItem>(32);

        if (dataId.endsWith(ConfigType.PROPERTIES.getType())) {
            Properties oldProp = new Properties();
            Properties newProp = new Properties();
            try {
                oldProp.load(new StringReader(oldContent));
                newProp.load(new StringReader(content));
                filterData(oldProp, newProp);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (dataId.endsWith(ConfigType.YML.getType()) || dataId.endsWith(ConfigType.YAML.getType())) {
            Yaml oldYaml = new Yaml();
            Yaml newYaml = new Yaml();
            Map<String, Object> oldMap = oldYaml.load(oldContent);
            Map<String, Object> newMap = newYaml.load(content);

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

}

