/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.remote;

import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.config.NacosConsoleAuthConfig;
import com.alibaba.nacos.console.handler.core.ClusterHandler;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NacosMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.hc.core5.http.HttpRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Common connector for remote server operations in console deployment mode.
 *
 * <p>Provides shared functionality for remote HTTP forwarding services, including
 * healthy member selection, authentication identity injection, and server context path resolution.</p>
 *
 * @author nacos
 */
@Component
@EnabledRemoteHandler
public class RemoteServerConnector {
    
    private final NacosMemberManager memberManager;
    
    private final ClusterHandler remoteClusterHandler;
    
    public RemoteServerConnector(NacosMemberManager memberManager,
        ClusterHandler remoteClusterHandler) {
        this.memberManager = memberManager;
        this.remoteClusterHandler = remoteClusterHandler;
    }
    
    /**
     * Add authentication identity headers to the HTTP request.
     *
     * @param request the HTTP request to add auth headers to
     */
    public void addAuthIdentity(HttpRequest request) {
        NacosAuthConfig authConfig = NacosAuthConfigHolder.getInstance()
            .getNacosAuthConfigByScope(NacosConsoleAuthConfig.NACOS_CONSOLE_AUTH_SCOPE);
        if (StringUtils.isNotBlank(authConfig.getServerIdentityKey())) {
            request.setHeader(authConfig.getServerIdentityKey(),
                authConfig.getServerIdentityValue());
        }
    }
    
    /**
     * Get the server context path for remote server.
     *
     * @return server context path, defaults to "/nacos"
     */
    public String getServerContextPath() {
        return EnvUtil.getProperty("nacos.console.remote.server.context-path", "/nacos");
    }
    
    /**
     * Randomly select one healthy member from the cluster.
     *
     * @return a healthy cluster member
     * @throws NacosException if no healthy server node is found
     */
    public Member randomOneHealthyMember() throws NacosException {
        Collection<Member> allMembers = memberManager.allMembers();
        Collection<? extends NacosMember> membersWithState = remoteClusterHandler.getNodeList("");
        Map<String, NodeState> nodeStateMap = membersWithState.stream()
            .collect(Collectors.toMap(NacosMember::getAddress, NacosMember::getState));
        allMembers.removeIf(node -> !NodeState.UP.equals(nodeStateMap.get(node.getAddress())));
        if (CollectionUtils.isEmpty(allMembers)) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR,
                "No healthy server node found.");
        }
        return allMembers.parallelStream().findAny().orElseThrow();
    }
}
