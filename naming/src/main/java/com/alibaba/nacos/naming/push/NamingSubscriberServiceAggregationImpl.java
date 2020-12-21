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

import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.NamingSubscriberServiceV2Impl;

import java.util.Collection;

/**
 * Aggregation naming subscriber service. Aggregate all implementation of {@link NamingSubscriberService} and
 * subscribers from other nodes.
 *
 * @author xiweng.yy
 */
@org.springframework.stereotype.Service
public class NamingSubscriberServiceAggregationImpl implements NamingSubscriberService {
    
    private final NamingSubscriberServiceV1Impl subscriberServiceV1;
    
    private final NamingSubscriberServiceV2Impl subscriberServiceV2;
    
    public NamingSubscriberServiceAggregationImpl(NamingSubscriberServiceV1Impl subscriberServiceV1,
            NamingSubscriberServiceV2Impl subscriberServiceV2) {
        this.subscriberServiceV1 = subscriberServiceV1;
        this.subscriberServiceV2 = subscriberServiceV2;
    }
    
    @Override
    public Collection<Subscriber> getSubscribers(String namespaceId, String serviceName) {
        return null;
    }
    
    @Override
    public Collection<Subscriber> getSubscribers(Service service) {
        return null;
    }
    
    @Override
    public Collection<Subscriber> getFuzzySubscribers(String namespaceId, String serviceName) {
        return null;
    }
    
    @Override
    public Collection<Subscriber> getFuzzySubscribers(Service service) {
        return null;
    }
}
