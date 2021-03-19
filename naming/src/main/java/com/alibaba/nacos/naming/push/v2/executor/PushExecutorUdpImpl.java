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

package com.alibaba.nacos.naming.push.v2.executor;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.UdpPushService;
import com.alibaba.nacos.naming.push.v2.PushDataWrapper;
import com.alibaba.nacos.naming.utils.ServiceUtil;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Push execute service for udp.
 *
 * @author xiweng.yy
 */
@Component
public class PushExecutorUdpImpl implements PushExecutor {
    
    private static final String UDP_PUSH_DATA_FOR_V1 = "udpPushDataForV1";
    
    private final UdpPushService pushService;
    
    public PushExecutorUdpImpl(UdpPushService pushService) {
        this.pushService = pushService;
    }
    
    @Override
    public void doPush(String clientId, Subscriber subscriber, PushDataWrapper data) {
        pushService.pushDataWithoutCallback(subscriber, handleClusterData(replaceServiceInfoName(data), subscriber));
    }
    
    @Override
    public void doPushWithCallback(String clientId, Subscriber subscriber, PushDataWrapper data,
            PushCallBack callBack) {
        pushService.pushDataWithCallback(subscriber, handleClusterData(replaceServiceInfoName(data), subscriber),
                callBack);
    }
    
    /**
     * The reason to replace the name is upd push is used in 1.x client. And 1.x client do not identify the group
     * attribute but only identify name attribute. So for supporting 1.x client, replace it with a new {@link
     * ServiceInfo}.
     *
     * <p>
     * Why not setName directly? Because the input {@link ServiceInfo} may be reused by 2.x push execute. And if set
     * name directly will has some effect for 2.x client.
     * </p>
     *
     * @param originalData original service info
     * @return new service info for 1.x
     */
    private ServiceInfo replaceServiceInfoName(PushDataWrapper originalData) {
        Optional<ServiceInfo> original = originalData.getProcessedPushData(UDP_PUSH_DATA_FOR_V1);
        if (original.isPresent()) {
            return original.get();
        }
        ServiceInfo serviceInfo = originalData.getOriginalData();
        ServiceInfo result = new ServiceInfo();
        result.setName(NamingUtils.getGroupedName(serviceInfo.getName(), serviceInfo.getGroupName()));
        result.setClusters(serviceInfo.getClusters());
        result.setHosts(serviceInfo.getHosts());
        result.setLastRefTime(serviceInfo.getLastRefTime());
        result.setCacheMillis(serviceInfo.getCacheMillis());
        originalData.addProcessedPushData(UDP_PUSH_DATA_FOR_V1, result);
        return result;
    }
    
    /**
     * For adapt push cluster feature for v1.x.
     *
     * @param data       original data
     * @param subscriber subscriber information
     * @return cluster filtered data
     * @deprecated Will be removed after client can filter cluster
     */
    @Deprecated
    private ServiceInfo handleClusterData(ServiceInfo data, Subscriber subscriber) {
        return StringUtils.isBlank(subscriber.getCluster()) ? data
                : ServiceUtil.selectInstances(data, subscriber.getCluster());
    }
}
