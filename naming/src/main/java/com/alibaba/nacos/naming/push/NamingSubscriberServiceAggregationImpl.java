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

package com.alibaba.nacos.naming.push;

import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.pojo.Subscribers;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Aggregation naming subscriber service. Aggregate all implementation of {@link NamingSubscriberService} and
 * subscribers from other nodes.
 *
 * @author xiweng.yy
 */
@org.springframework.stereotype.Service
public class NamingSubscriberServiceAggregationImpl implements NamingSubscriberService {
    
    private static final String SUBSCRIBER_ON_SYNC_URL = "/service/subscribers";
    
    private final NamingSubscriberServiceLocalImpl subscriberServiceLocal;
    
    private final ServerMemberManager memberManager;
    
    public NamingSubscriberServiceAggregationImpl(NamingSubscriberServiceLocalImpl subscriberServiceLocal,
            ServerMemberManager serverMemberManager) {
        this.subscriberServiceLocal = subscriberServiceLocal;
        this.memberManager = serverMemberManager;
    }
    
    @Override
    public Collection<Subscriber> getSubscribers(String namespaceId, String serviceName) {
        Collection<Subscriber> result = new LinkedList<>(
                subscriberServiceLocal.getSubscribers(namespaceId, serviceName));
        if (memberManager.getServerList().size() > 1) {
            getSubscribersFromRemotes(namespaceId, serviceName, result);
        }
        return result;
    }
    
    @Override
    public Collection<Subscriber> getSubscribers(Service service) {
        Collection<Subscriber> result = new LinkedList<>(subscriberServiceLocal.getSubscribers(service));
        if (memberManager.getServerList().size() > 1) {
            getSubscribersFromRemotes(service.getNamespace(), service.getGroupedServiceName(), result);
        }
        return result;
    }
    
    @Override
    public Collection<Subscriber> getFuzzySubscribers(String namespaceId, String serviceName) {
        Collection<Subscriber> result = new LinkedList<>(
                subscriberServiceLocal.getFuzzySubscribers(namespaceId, serviceName));
        if (memberManager.getServerList().size() > 1) {
            getSubscribersFromRemotes(namespaceId, serviceName, result);
        }
        return result;
    }
    
    @Override
    public Collection<Subscriber> getFuzzySubscribers(Service service) {
        Collection<Subscriber> result = new LinkedList<>(subscriberServiceLocal.getFuzzySubscribers(service));
        if (memberManager.getServerList().size() > 1) {
            getSubscribersFromRemotes(service.getNamespace(), service.getGroupedServiceName(), result);
        }
        return result;
    }
    
    private void getSubscribersFromRemotes(String namespaceId, String serviceName, Collection<Subscriber> result) {
        for (Member server : memberManager.allMembersWithoutSelf()) {
            Map<String, String> paramValues = new HashMap<>(128);
            paramValues.put(CommonParams.SERVICE_NAME, serviceName);
            paramValues.put(CommonParams.NAMESPACE_ID, namespaceId);
            paramValues.put("aggregation", String.valueOf(Boolean.FALSE));
            // TODO replace with gRPC
            RestResult<String> response = HttpClient.httpGet(
                    "http://" + server.getAddress() + EnvUtil.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT
                            + SUBSCRIBER_ON_SYNC_URL, new ArrayList<>(), paramValues);
            if (response.ok()) {
                Subscribers subscribers = JacksonUtils.toObj(response.getData(), Subscribers.class);
                result.addAll(subscribers.getSubscribers());
            }
        }
    }
}
