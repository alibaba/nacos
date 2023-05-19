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

package com.alibaba.nacos.core.controller;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Cluster communication interface.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@RestController
@RequestMapping(Commons.NACOS_CORE_CONTEXT + "/cluster")
public class NacosClusterController {
    
    private final ServerMemberManager memberManager;
    
    public NacosClusterController(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
    }
    
    @GetMapping(value = "/self")
    public RestResult<Member> self() {
        return RestResultUtils.success(memberManager.getSelf());
    }
    
    /**
     * The console displays the list of cluster members.
     *
     * @param ipKeyWord search keyWord
     * @return all members
     */
    @GetMapping(value = "/nodes")
    public RestResult<Collection<Member>> listNodes(
            @RequestParam(value = "keyword", required = false) String ipKeyWord) {
        Collection<Member> members = memberManager.allMembers();
        Collection<Member> result = new ArrayList<>();
        
        members.stream().sorted().forEach(member -> {
            if (StringUtils.isBlank(ipKeyWord)) {
                result.add(member);
                return;
            }
            final String address = member.getAddress();
            if (StringUtils.equals(address, ipKeyWord) || StringUtils.startsWith(address, ipKeyWord)) {
                result.add(member);
            }
        });
        
        return RestResultUtils.success(result);
    }
    
    // The client can get all the nacos node information in the current
    // cluster according to this interface
    
    @GetMapping(value = "/simple/nodes")
    public RestResult<Collection<String>> listSimpleNodes() {
        return RestResultUtils.success(memberManager.getMemberAddressInfos());
    }
    
    @GetMapping("/health")
    public RestResult<String> getHealth() {
        return RestResultUtils.success(memberManager.getSelf().getState().name());
    }
    
    /**
     * Other nodes return their own metadata information.
     *
     * @param node {@link Member}
     * @return {@link RestResult}
     */
    @Deprecated
    @PostMapping(value = {"/report"})
    public RestResult<String> report(@RequestBody Member node) {
        if (!node.check()) {
            return RestResultUtils.failedWithMsg(400, "Node information is illegal");
        }
        LoggerUtils.printIfDebugEnabled(Loggers.CLUSTER, "node state report, receive info : {}", node);
        node.setState(NodeState.UP);
        node.setFailAccessCnt(0);
        memberManager.update(node);
        return RestResultUtils.success(JacksonUtils.toJson(memberManager.getSelf()));
    }
    
    /**
     * Addressing mode switch.
     *
     * @param type member-lookup name
     * @return {@link RestResult}
     */
    @PostMapping(value = "/switch/lookup")
    public RestResult<String> switchLookup(@RequestParam(name = "type") String type) {
        try {
            memberManager.switchLookup(type);
            return RestResultUtils.success();
        } catch (Throwable ex) {
            return RestResultUtils.failed(ex.getMessage());
        }
    }
    
    /**
     * member leave.
     *
     * @param params member ip list, example [ip1:port1,ip2:port2,...]
     * @return {@link RestResult}
     * @throws Exception {@link Exception}
     */
    @PostMapping("/server/leave")
    public RestResult<String> leave(@RequestBody Collection<String> params,
            @RequestParam(defaultValue = "true") Boolean notifyOtherMembers) throws Exception {
        return RestResultUtils.failed(405, "/v1/core/cluster/server/leave API not allow to use temporarily.");
    }
    
}
