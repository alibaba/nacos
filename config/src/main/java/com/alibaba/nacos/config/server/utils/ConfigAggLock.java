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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.core.distributed.raft.jraft.JRaftProtocol;
import com.alibaba.nacos.core.utils.SpringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Data aggregation tasks need to adopt lock contention in cluster
 * mode to ensure that data aggregation tasks are initiated by a node
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public class ConfigAggLock {

    private final JRaftProtocol protocol;

    public ConfigAggLock(JRaftProtocol protocol) {
        this.protocol = protocol;
    }

    public boolean lock() {

        // If using external storage, no real competition for resources is required

        final String val = SpringUtils.getProperty("nacos.config.store.type");
        if (StringUtils.equalsIgnoreCase(val, "separate")) {
            return true;
        }
        Map<String, Object> metaData = protocol.protocolMetaData();
        return (boolean) metaData.get("leader");
    }

    public void unLock() {
        // do nothing
    }
}
