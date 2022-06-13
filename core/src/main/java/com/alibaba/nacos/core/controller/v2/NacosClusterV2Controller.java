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

package com.alibaba.nacos.core.controller.v2;

import com.alibaba.nacos.common.Beta;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberUtil;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.model.request.LookupUpdateRequest;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Cluster communication interface v2.
 *
 * @author wuzhiguo
 */
@Beta
@RestController
@RequestMapping(Commons.NACOS_CORE_CONTEXT_V2 + "/cluster")
public class NacosClusterV2Controller {
    
    private final ServerMemberManager memberManager;
    
    public NacosClusterV2Controller(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
    }
    
    @GetMapping(value = "/nodes/self")
    public RestResult<Member> self() {
        return RestResultUtils.success(memberManager.getSelf());
    }
    
    /**
     * The console displays the list of cluster members.
     *
     * @param address match address
     * @param state match state
     *
     * @return members that matches condition
     */
    @GetMapping(value = "/nodes")
    public RestResult<Collection<Member>> listNodes(
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "state", required = false) String state) {
        
        NodeState nodeState = null;
        if (StringUtils.isNoneBlank(state)) {
            try {
                nodeState = NodeState.valueOf(state.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return RestResultUtils.failedWithMsg(400, "Illegal state: " + state);
            }
        }
        
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
        
        return RestResultUtils.success(result);
    }
    
    // The client can get all the nacos node information in the current
    // cluster according to this interface
    
    /**
     * Other nodes return their own metadata information.
     *
     * @param nodes List of {@link Member}
     * @return {@link RestResult}
     */
    @PutMapping(value = "/nodes")
    public RestResult<Void> updateNodes(@RequestBody List<Member> nodes) {
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
        
        return RestResultUtils.success();
    }
    
    /**
     * Addressing mode switch.
     *
     * @param request {@link LookupUpdateRequest}
     * @return {@link RestResult}
     */
    @PutMapping(value = "/lookup")
    public RestResult<Void> updateLookup(@RequestBody LookupUpdateRequest request) {
        try {
            memberManager.switchLookup(request.getType());
            return RestResultUtils.success();
        } catch (Throwable ex) {
            return RestResultUtils.failed(ex.getMessage());
        }
    }
    
    /**
     * member leave.
     *
     * @param addresses member ip list, example [ip1:port1,ip2:port2,...]
     * @return {@link RestResult}
     * @throws Exception throw {@link Exception}
     */
    @DeleteMapping("/nodes")
    public RestResult<Void> deleteNodes(@RequestParam("addresses") List<String> addresses) throws Exception {
        Collection<Member> memberList = MemberUtil.multiParse(addresses);
        memberManager.memberLeave(memberList);
        final NacosAsyncRestTemplate nacosAsyncRestTemplate = HttpClientBeanHolder.getNacosAsyncRestTemplate(
                Loggers.CLUSTER);
        final GenericType<RestResult<String>> genericType = new GenericType<RestResult<String>>() {
        };
        final Collection<Member> notifyList = memberManager.allMembersWithoutSelf();
        notifyList.removeAll(memberList);
        CountDownLatch latch = new CountDownLatch(notifyList.size());
        for (Member member : notifyList) {
            final String url = HttpUtils.buildUrl(false, member.getAddress(), EnvUtil.getContextPath(),
                    Commons.NACOS_CORE_CONTEXT_V2, "/cluster/nodes");
            nacosAsyncRestTemplate.delete(url, Header.EMPTY, StringUtils.join(addresses, ","), genericType.getType(),
                    new Callback<Void>() {
                        @Override
                        public void onReceive(RestResult<Void> result) {
                            try {
                                if (result.ok()) {
                                    LoggerUtils.printIfDebugEnabled(Loggers.CLUSTER,
                                            "The node : [{}] success to process the request", member);
                                    MemberUtil.onSuccess(memberManager, member);
                                } else {
                                    Loggers.CLUSTER.warn(
                                            "The node : [{}] failed to process the request, response is : {}", member,
                                            result);
                                    MemberUtil.onFail(memberManager, member);
                                }
                            } finally {
                                latch.countDown();
                            }
                        }
                        
                        @Override
                        public void onError(Throwable throwable) {
                            try {
                                Loggers.CLUSTER.error("Failed to communicate with the node : {}", member);
                                MemberUtil.onFail(memberManager, member);
                            } finally {
                                latch.countDown();
                            }
                        }
                        
                        @Override
                        public void onCancel() {
                        
                        }
                    });
        }
        
        try {
            latch.await(10_000, TimeUnit.MILLISECONDS);
            return RestResultUtils.success();
        } catch (Throwable ex) {
            return RestResultUtils.failed(ex.getMessage());
        }
    }
    
}
