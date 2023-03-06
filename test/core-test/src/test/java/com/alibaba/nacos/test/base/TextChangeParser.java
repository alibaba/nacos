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

package com.alibaba.nacos.test.base;

import com.alibaba.nacos.api.config.ConfigChangeItem;
import com.alibaba.nacos.api.config.PropertyChangeType;
import com.alibaba.nacos.api.config.listener.ConfigChangeParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TextChangeParser implements ConfigChangeParser {
    @Override
    public boolean isResponsibleFor(String type) {
        return (null == type || "text".equalsIgnoreCase(type));
    }

    @Override
    public Map<String, ConfigChangeItem> doParse(String oldContent, String newContent, String type) throws IOException {
        Map<String, ConfigChangeItem> map = new HashMap<>(4);
        final String key = "content";

        ConfigChangeItem cci = new ConfigChangeItem(key, oldContent, newContent);
        if (null == oldContent && null != newContent) {
            cci.setType(PropertyChangeType.ADDED);
        } else if (null != oldContent && null != newContent && !oldContent.equals(newContent)) {
            cci.setType(PropertyChangeType.MODIFIED);
        } else if (null != oldContent && null == newContent) {
            cci.setType(PropertyChangeType.DELETED);
        }
        map.put(key, cci);

        return map;
    }
}

