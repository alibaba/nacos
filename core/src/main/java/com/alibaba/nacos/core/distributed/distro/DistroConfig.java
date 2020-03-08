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

package com.alibaba.nacos.core.distributed.distro;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ap.LogProcessor4AP;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * // TODO 支持 hot-update
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
@ConfigurationProperties(prefix = "nacos.core.protocol.distro")
public class DistroConfig implements Config<LogProcessor4AP> {

    private static final long serialVersionUID = -3073040842709279788L;

    private Map<String, String> data = Collections.synchronizedMap(new HashMap<>(8));

    private List<LogProcessor4AP> processors = Collections.synchronizedList(new ArrayList<>());

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = Collections.synchronizedMap(data);
    }

    @Override
    public void setVal(String key, String value) {
        data.put(key, value);
    }

    @Override
    public String getVal(String key) {
        return data.get(key);
    }

    @Override
    public String getValOfDefault(String key, String defaultVal) {
        return data.getOrDefault(key, defaultVal);
    }

    @Override
    public List<LogProcessor4AP> listLogProcessor() {
        return Collections.unmodifiableList(processors);
    }

    @Override
    public void addLogProcessors(Collection<LogProcessor4AP> processors) {
        this.processors.addAll(processors);
    }

    @Override
    public String toString() {
        return JSON.toJSONString(data);
    }

}
