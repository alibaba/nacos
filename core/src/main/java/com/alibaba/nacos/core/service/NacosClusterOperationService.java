/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.model.request.LookupUpdateRequest;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * NacosClusterOperationService.
 * @author dongyafei
 * @date 2022/8/15
 */
@Service
public class NacosClusterOperationService {
    
    private final ServerMemberManager memberManager;
    
    public NacosClusterOperationService(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
    }
    
    public Member self() {
        return memberManager.getSelf();
    }
    
    /**
     * The console displays the list of cluster members.
     */
    public Collection<Member> listNodes(String address, NodeState nodeState) throws NacosException {
        
        Collection<Member> members = memberManager.allMembers();
        Collection<Member> result = new ArrayList<>();
        
        for (Member member : members) {
            if (StringUtils.isNoneBlank(address) && !StringUtils.startsWith(member.getAddress(), address)) {
                continue;
            }
            if (nodeState != null && member.getState() != nodeState) {
                continue;
            }
            result.add(member);
        }
        return result;
    }
    
    /**
     * cluster members information update.
     */
    public Boolean updateNodes(List<Member> nodes) {
        for (Member node : nodes) {
            if (!node.check()) {
                LoggerUtils.printIfWarnEnabled(Loggers.CLUSTER, "node information is illegal, ignore node: {}", node);
                continue;
            }
            
            LoggerUtils.printIfDebugEnabled(Loggers.CLUSTER, "node state updating, node: {}", node);
            node.setState(NodeState.UP);
            node.setFailAccessCnt(0);
            
            boolean update = memberManager.update(node);
            if (!update) {
                LoggerUtils.printIfErrorEnabled(Loggers.CLUSTER, "node state update failed, node: {}", node);
            }
        }
        return true;
    }
    
    /**
     * Addressing mode switch.
     */
    public Boolean updateLookup(LookupUpdateRequest request) throws NacosException {
        memberManager.switchLookup(request.getType());
        return true;
    }
    
    /**
     * query health of current node.
     */
    public String selfHealth() {
        return memberManager.getSelf().getState().name();
    }
}
