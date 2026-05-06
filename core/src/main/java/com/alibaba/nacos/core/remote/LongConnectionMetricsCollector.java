/*
 *
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.plugin.control.connection.ConnectionMetricsCollector;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * long connection metrics collector.
 *
 * @author shiyiyue
 */
public class LongConnectionMetricsCollector implements ConnectionMetricsCollector {
    
    @Override
    public String getName() {
        return "long_connection";
    }
    
    @Override
    public int getTotalCount() {
        return ApplicationUtils.getBean(ConnectionManager.class).currentClientsCount();
    }
    
    @Override
    public int getCountForIp(String ip) {
        ConnectionManager connectionManager = ApplicationUtils.getBean(ConnectionManager.class);
        if (connectionManager.getConnectionForClientIp().containsKey(ip)) {
            return connectionManager.getConnectionForClientIp().get(ip).intValue();
        } else {
            return 0;
        }
    }
}