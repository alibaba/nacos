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

import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberUtil;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.core.utils.Loggers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    @PostMapping(value = {"/report"})
    public RestResult<String> report(@RequestBody Member node) {
        if (!node.check()) {
            return RestResultUtils.failedWithMsg(400, "Node information is illegal");
        }
        LoggerUtils.printIfDebugEnabled(Loggers.CLUSTER, "node state report, receive info : {}", node);
        node.setState(NodeState.UP);
        node.setFailAccessCnt(0);
        
        boolean result = memberManager.update(node);
        
        return RestResultUtils.success(Boolean.toString(result));
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
    public RestResult<String> leave(@RequestBody Collection<String> params) throws Exception {
        Collection<Member> memberList = MemberUtil.multiParse(params);
        memberManager.memberLeave(memberList);
        final NacosAsyncRestTemplate nacosAsyncRestTemplate = HttpClientBeanHolder.getNacosAsyncRestTemplate(Loggers.CLUSTER);
        final GenericType<RestResult<String>> genericType = new GenericType<RestResult<String>>() {
        };
        final Collection<Member> notifyList = memberManager.allMembersWithoutSelf();
        notifyList.removeAll(memberList);
        CountDownLatch latch = new CountDownLatch(notifyList.size());
        for (Member member : notifyList) {
            final String url = HttpUtils
                    .buildUrl(false, member.getAddress(), EnvUtil.getContextPath(), Commons.NACOS_CORE_CONTEXT,
                            "/cluster/server/leave");
            nacosAsyncRestTemplate.post(url, Header.EMPTY, Query.EMPTY, params, genericType.getType(), new Callback<String>() {
                @Override
                public void onReceive(RestResult<String> result) {
                    try {
                        if (result.ok()) {
                            LoggerUtils.printIfDebugEnabled(Loggers.CLUSTER,
                                    "The node : [{}] success to process the request", member);
                            MemberUtil.onSuccess(memberManager, member);
                        } else {
                            Loggers.CLUSTER
                                    .warn("The node : [{}] failed to process the request, response is : {}", member,
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
            return RestResultUtils.success("ok");
        } catch (Throwable ex) {
            return RestResultUtils.failed(ex.getMessage());
        }
    }
    
}
