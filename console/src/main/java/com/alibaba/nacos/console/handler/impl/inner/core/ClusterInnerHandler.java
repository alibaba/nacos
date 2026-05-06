/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.inner.core;

import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.console.handler.core.ClusterHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Implementation of ClusterHandler that handles cluster-related operations.
 *
 * @author zhangyukun
 */
@Service
@EnabledInnerHandler
public class ClusterInnerHandler implements ClusterHandler {
    
    private final ServerMemberManager memberManager;
    
    /**
     * Constructs a new ClusterInnerHandler with the provided dependencies.
     *
     * @param memberManager the manager for server members
     */
    @Autowired
    public ClusterInnerHandler(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
    }
    
    @Override
    public Collection<? extends NacosMember> getNodeList(String ipKeyWord) {
        return memberManager.allMembers();
    }
}
