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

package com.alibaba.nacos.naming.cluster.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.cluster.remote.grpc.GrpcClusterClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Cluster client proxy.
 *
 * @author xiweng.yy
 */
@Component
public class ClusterClientManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterClientManager.class);
    
    private final ConcurrentMap<String, ClusterClient> clientMap = new ConcurrentHashMap<>();
    
    private final ServerMemberManager memberManager;
    
    public ClusterClientManager(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
    }
    
    /**
     * Init cluster client manager.
     */
    @PostConstruct
    public void init() {
        for (Member each : memberManager.allMembersWithoutSelf()) {
            clientMap.put(each.getAddress(), new GrpcClusterClient(each.getAddress()));
        }
        for (ClusterClient each : clientMap.values()) {
            try {
                each.start();
            } catch (NacosException nacosException) {
                LOGGER.error("Create cluster connection failed", nacosException);
            }
        }
    }
    
    public boolean hasClientForMember(String memberAddress) {
        return clientMap.containsKey(memberAddress);
    }
    
    public ClusterClient getClusterClient(String memberAddress) {
        return clientMap.getOrDefault(memberAddress, null);
    }
    
    public Collection<ClusterClient> getAllClusterClient() {
        return clientMap.values();
    }
}
