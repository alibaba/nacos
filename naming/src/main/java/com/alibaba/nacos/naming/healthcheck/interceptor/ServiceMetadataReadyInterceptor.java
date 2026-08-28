/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.healthcheck.interceptor;

import com.alibaba.nacos.consistency.ProtocolMetaData;
import com.alibaba.nacos.consistency.cp.MetadataKey;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.naming.healthcheck.NacosHealthCheckTask;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * Intercept persistent instance health checks until service metadata is ready.
 *
 * @author Zhengcy05
 */
public class ServiceMetadataReadyInterceptor extends AbstractHealthCheckInterceptor {
    
    @Override
    public boolean intercept(NacosHealthCheckTask object) {
        try {
            ProtocolMetaData protocolMetaData = ApplicationUtils.getBean(ProtocolManager.class)
                .getCpProtocol().protocolMetaData();
            Object leaderMetadata = protocolMetaData.get(Constants.SERVICE_METADATA,
                MetadataKey.LEADER_META_DATA);
            if (!(leaderMetadata instanceof ProtocolMetaData.ValueItem)) {
                return true;
            }
            return null == ((ProtocolMetaData.ValueItem) leaderMetadata).getData();
        } catch (Exception e) {
            return true;
        }
    }
    
    @Override
    public boolean isInterceptType(Class<?> type) {
        return HealthCheckTaskV2.class.isAssignableFrom(type);
    }
    
    @Override
    public int order() {
        return Integer.MIN_VALUE + 2;
    }
}
