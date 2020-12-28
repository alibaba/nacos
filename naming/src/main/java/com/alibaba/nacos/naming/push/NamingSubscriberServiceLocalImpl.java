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
import com.alibaba.nacos.naming.push.v1.NamingSubscriberServiceV1Impl;
import com.alibaba.nacos.naming.push.v2.NamingSubscriberServiceV2Impl;

import java.util.Collection;
import java.util.HashSet;

/**
 * Naming subscriber service for local.
 *
 * @author xiweng.yy
 * @deprecated Will be removed with {@link com.alibaba.nacos.naming.push.v1.NamingSubscriberServiceV1Impl}
 */
@org.springframework.stereotype.Service
@Deprecated
public class NamingSubscriberServiceLocalImpl implements NamingSubscriberService {
    
    private final NamingSubscriberServiceV1Impl namingSubscriberServiceV1;
    
    private final NamingSubscriberServiceV2Impl namingSubscriberServiceV2;
    
    public NamingSubscriberServiceLocalImpl(NamingSubscriberServiceV1Impl namingSubscriberServiceV1,
            NamingSubscriberServiceV2Impl namingSubscriberServiceV2) {
        this.namingSubscriberServiceV1 = namingSubscriberServiceV1;
        this.namingSubscriberServiceV2 = namingSubscriberServiceV2;
    }
    
    @Override
    public Collection<Subscriber> getSubscribers(String namespaceId, String serviceName) {
        Collection<Subscriber> result = new HashSet<>();
        result.addAll(namingSubscriberServiceV1.getSubscribers(namespaceId, serviceName));
        result.addAll(namingSubscriberServiceV2.getSubscribers(namespaceId, serviceName));
        return result;
    }
    
    @Override
    public Collection<Subscriber> getSubscribers(Service service) {
        Collection<Subscriber> result = new HashSet<>();
        result.addAll(namingSubscriberServiceV1.getSubscribers(service));
        result.addAll(namingSubscriberServiceV2.getSubscribers(service));
        return result;
    }
    
    @Override
    public Collection<Subscriber> getFuzzySubscribers(String namespaceId, String serviceName) {
        Collection<Subscriber> result = new HashSet<>();
        result.addAll(namingSubscriberServiceV1.getFuzzySubscribers(namespaceId, serviceName));
        result.addAll(namingSubscriberServiceV2.getFuzzySubscribers(namespaceId, serviceName));
        return result;
    }
    
    @Override
    public Collection<Subscriber> getFuzzySubscribers(Service service) {
        Collection<Subscriber> result = new HashSet<>();
        result.addAll(namingSubscriberServiceV1.getFuzzySubscribers(service));
        result.addAll(namingSubscriberServiceV2.getFuzzySubscribers(service));
        return result;
    }
}
