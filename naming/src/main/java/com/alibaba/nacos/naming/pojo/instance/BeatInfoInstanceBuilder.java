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

package com.alibaba.nacos.naming.pojo.instance;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.builder.InstanceBuilder;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.naming.healthcheck.RsInfo;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

/**
 * Client beatInfo instance builder.
 *
 * @author xiweng.yy
 */
public class BeatInfoInstanceBuilder {
    
    private final InstanceBuilder actualBuilder;
    
    private final Collection<InstanceExtensionHandler> handlers;
    
    private BeatInfoInstanceBuilder() {
        this.actualBuilder = InstanceBuilder.newBuilder();
        this.handlers = NacosServiceLoader.newServiceInstances(InstanceExtensionHandler.class);
    }
    
    public static BeatInfoInstanceBuilder newBuilder() {
        return new BeatInfoInstanceBuilder();
    }
    
    /**
     * Build a new {@link Instance} and chain handled by {@link InstanceExtensionHandler}.
     *
     * @return new instance
     */
    public Instance build() {
        Instance result = actualBuilder.build();
        for (InstanceExtensionHandler each : handlers) {
            each.handleExtensionInfo(result);
        }
        setInstanceId(result);
        return result;
    }
    
    public BeatInfoInstanceBuilder setRequest(HttpServletRequest request) {
        for (InstanceExtensionHandler each : handlers) {
            each.configExtensionInfoFromRequest(request);
        }
        return this;
    }
    
    public BeatInfoInstanceBuilder setServiceName(String serviceName) {
        actualBuilder.setServiceName(serviceName);
        return this;
    }
    
    public BeatInfoInstanceBuilder setBeatInfo(RsInfo beatInfo) {
        setAttributesToBuilder(beatInfo);
        return this;
    }
    
    private void setAttributesToBuilder(RsInfo beatInfo) {
        actualBuilder.setPort(beatInfo.getPort());
        actualBuilder.setIp(beatInfo.getIp());
        actualBuilder.setWeight(beatInfo.getWeight());
        actualBuilder.setMetadata(beatInfo.getMetadata());
        actualBuilder.setClusterName(beatInfo.getCluster());
        actualBuilder.setEphemeral(beatInfo.isEphemeral());
    }
    
    /**
     * TODO use spi and metadata info to generate instanceId.
     */
    private void setInstanceId(Instance instance) {
        DefaultInstanceIdGenerator idGenerator = new DefaultInstanceIdGenerator(instance.getServiceName(),
                instance.getClusterName(), instance.getIp(), instance.getPort());
        instance.setInstanceId(idGenerator.generateInstanceId());
    }
}
