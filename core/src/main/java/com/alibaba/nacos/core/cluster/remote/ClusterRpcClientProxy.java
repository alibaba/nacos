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

package com.alibaba.nacos.core.cluster.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberChangeListener;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.nacos.api.exception.NacosException.CLIENT_INVALID_PARAM;

/**
 * cluster rpc client proxy.
 *
 * @author liuzunfei
 * @version $Id: ClusterRpcClientProxy.java, v 0.1 2020年08月11日 2:11 PM liuzunfei Exp $
 */
@Service
public class ClusterRpcClientProxy extends MemberChangeListener {
    
    @Autowired
    ServerMemberManager serverMemberManager;
    
    /**
     * init cluster rpc clients.
     *
     * @param members cluster server list member list.
     */
    private void refresh(List<Member> members) throws NacosException {
        
        //ensure to create client of new members
        for (Member member : members) {
            createRpcClientAndStart(member);
        }
        
        //shutdown and remove old members.
        Set<Map.Entry<String, RpcClient>> allClientEntrys = RpcClientFactory.getAllClientEntrys();
        Iterator<Map.Entry<String, RpcClient>> iterator = allClientEntrys.iterator();
        List<String> newMemberKeys = members.stream().map(a -> memberClientKey(a)).collect(Collectors.toList());
        while (iterator.hasNext()) {
            Map.Entry<String, RpcClient> next1 = iterator.next();
            if (next1.getKey().startsWith("Cluster-") && !newMemberKeys.contains(next1.getKey())) {
                next1.getValue().shutdown();
                iterator.remove();
            }
        }
        
    }
    
    private String memberClientKey(Member member) {
        return "Cluster-" + member.getAddress();
    }
    
    private void createRpcClientAndStart(Member member) throws NacosException {
        RpcClient client = RpcClientFactory.createClient(memberClientKey(member), ConnectionType.RSOCKET);
        if (client.isWaitInited()) {
            //fixed server
            client.init(new ServerListFactory() {
                @Override
                public String genNextServer() {
                    return member.getAddress();
                }
                
                @Override
                public String getCurrentServer() {
                    return member.getAddress();
                }
            });
            client.start();
        }
    }
    
    /**
     * send request to member.
     *
     * @param member
     * @param request
     * @return
     * @throws NacosException
     */
    public Response sendRequest(Member member, Request request) throws NacosException {
        RpcClient client = RpcClientFactory.getClient(memberClientKey(member));
        if (client != null) {
            Response response = client.request(request);
            return response;
        } else {
            throw new NacosException(CLIENT_INVALID_PARAM, "No rpc client related to member: " + member);
        }
    }
    
    @Override
    public void onEvent(MembersChangeEvent event) {
        try {
            List<Member> members = serverMemberManager.allMembersWithoutSelf();
            refresh(members);
        } catch (NacosException e) {
            Loggers.CLUSTER.warn("[serverlist] fail to refresh cluster rpc client ", event);
        }
    }
}
