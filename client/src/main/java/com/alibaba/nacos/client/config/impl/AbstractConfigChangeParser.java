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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * AbstractConfigChangeParser
 *
 * @author rushsky518
 */
public abstract class AbstractConfigChangeParser {
    private String configType;

    private AbstractConfigChangeParser next;

    public AbstractConfigChangeParser(String configType) {
        this.configType = configType;
    }

    public AbstractConfigChangeParser addNext(AbstractConfigChangeParser configChangeParser) {
        if (null == this.next) {
            this.next = configChangeParser;
        } else {
            this.next.addNext(configChangeParser);
        }
        return this;
    }

    protected boolean isResponsibleFor(String type) {
        return this.configType.equalsIgnoreCase(type);
    }

    public Map parseChangeData(String oldContent, String newContent, String type) throws IOException {
        if (isResponsibleFor(type)) {
            return this.doParse(oldContent, newContent, type);
        }

        if (null != this.next) {
            return this.next.parseChangeData(oldContent, newContent, type);
        }

        return Collections.emptyMap();
    }

    /**
     * parse and compare config data
     * @param oldContent
     * @param newContent
     * @param type
     * @return
     * @throws IOException
     */
    protected abstract Map<String, Object> doParse(String oldContent, String newContent, String type) throws IOException;

    protected Map filterChangeData(Map oldMap, Map newMap) {
        Map<String, ConfigChangeItem> result = new HashMap<String, ConfigChangeItem>(16);
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

        return result;
    }

}
