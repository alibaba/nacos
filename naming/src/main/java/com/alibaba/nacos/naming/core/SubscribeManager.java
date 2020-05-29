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
package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.pojo.Subscribers;
import com.alibaba.nacos.naming.push.PushService;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Nicholas
 * @since 1.0.1
 */
@Service
public class SubscribeManager {

    private static final String SUBSCRIBER_ON_SYNC_URL = "/service/subscribers";

    @Autowired
    private PushService pushService;

    @Autowired
    private ServerMemberManager memberManager;


    private List<Subscriber> getSubscribers(String serviceName, String namespaceId) {
        return pushService.getClients(serviceName, namespaceId);
    }

    private List<Subscriber> getSubscribersFuzzy(String serviceName, String namespaceId) {
        return pushService.getClientsFuzzy(serviceName, namespaceId);
    }

    /**
     * @param serviceName
     * @param namespaceId
     * @param aggregation
     * @return
     * @throws InterruptedException
     */
    public List<Subscriber> getSubscribers(String serviceName, String namespaceId, boolean aggregation) throws InterruptedException {
        if (aggregation) {
            // size = 1 means only myself in the list, we need at least one another server alive:
            if (memberManager.getServerList().size() <= 1) {
                return getSubscribersFuzzy(serviceName, namespaceId);
            }

            List<Subscriber> subscriberList = new ArrayList<Subscriber>();
            // try sync data from remote server:
            for (Member server : memberManager.allMembers()) {

                Map<String, String> paramValues = new HashMap<>(128);
                paramValues.put(CommonParams.SERVICE_NAME, serviceName);
                paramValues.put(CommonParams.NAMESPACE_ID, namespaceId);
                paramValues.put("aggregation", String.valueOf(Boolean.FALSE));
                if (NetUtils.localServer().equals(server.getAddress())) {
                    subscriberList.addAll(getSubscribersFuzzy(serviceName, namespaceId));
                    continue;
                }

                HttpClient.HttpResult result = HttpClient.httpGet("http://" + server.getAddress() + ApplicationUtils
                        .getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + SUBSCRIBER_ON_SYNC_URL, new ArrayList<>(), paramValues);

                if (HttpURLConnection.HTTP_OK == result.code) {
                    Subscribers subscribers = JacksonUtils.toObj(result.content, Subscribers.class);
                    subscriberList.addAll(subscribers.getSubscribers());
                }
            }
            return CollectionUtils.isNotEmpty(subscriberList) ?
                subscriberList.stream().filter(distinctByKey(Subscriber::toString)).collect(Collectors.toList())
                : Collections.EMPTY_LIST;
        } else {
            // local server
            return getSubscribersFuzzy(serviceName, namespaceId);
        }
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>(128);
        return object -> seen.putIfAbsent(keyExtractor.apply(object), Boolean.TRUE) == null;
    }
}
