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

import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.NamingSubscriberServiceAggregationImpl;
import com.alibaba.nacos.naming.push.NamingSubscriberServiceLocalImpl;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Subscribe manager.
 *
 * @author Nicholas
 * @author xiweng.yy
 * @since 1.0.1
 */
@Service
public class SubscribeManager {
    
    @Autowired
    private NamingSubscriberServiceLocalImpl localService;
    
    @Autowired
    private NamingSubscriberServiceAggregationImpl aggregationService;
    
    /**
     * Get subscribers.
     *
     * @param serviceName service name
     * @param namespaceId namespace id
     * @param aggregation aggregation
     * @return list of subscriber
     */
    public List<Subscriber> getSubscribers(String serviceName, String namespaceId, boolean aggregation) {
        if (aggregation) {
            Collection<Subscriber> result = aggregationService.getFuzzySubscribers(namespaceId, serviceName);
            return CollectionUtils.isNotEmpty(result) ? result.stream().filter(distinctByKey(Subscriber::toString))
                    .collect(Collectors.toList()) : Collections.EMPTY_LIST;
        } else {
            return new LinkedList<>(localService.getFuzzySubscribers(namespaceId, serviceName));
        }
    }
    
    public static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>(128);
        return object -> seen.putIfAbsent(keyExtractor.apply(object), Boolean.TRUE) == null;
    }
}
