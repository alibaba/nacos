/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import org.springframework.stereotype.Component;

/**
 * TCP health check processor for v2.x.
 *
 * @author xiweng.yy
 */
@Component
public class TcpHealthCheckProcessor implements HealthCheckProcessorV2 {
    
    public static final String TYPE = HealthCheckType.TCP.name();
    
    @Override
    public void process(InstancePublishInfo publishInfo, ClusterMetadata metadata) {
        // TODO.
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
}
