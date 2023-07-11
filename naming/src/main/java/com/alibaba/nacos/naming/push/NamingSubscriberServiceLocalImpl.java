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
import java.util.HashSet;

/**
 * Naming subscriber service for local.
 *
 * @author xiweng.yy
 */
@org.springframework.stereotype.Service
public class NamingSubscriberServiceLocalImpl implements NamingSubscriberService {
    
    private final NamingSubscriberServiceV2Impl namingSubscriberServiceV2;
    
    public NamingSubscriberServiceLocalImpl(NamingSubscriberServiceV2Impl namingSubscriberServiceV2) {
        this.namingSubscriberServiceV2 = namingSubscriberServiceV2;
    }
    
    @Override
    public Collection<Subscriber> getSubscribers(String namespaceId, String serviceName) {
        return new HashSet<>(namingSubscriberServiceV2.getSubscribers(namespaceId, serviceName));
    }
    
    @Override
    public Collection<Subscriber> getSubscribers(Service service) {
        return new HashSet<>(namingSubscriberServiceV2.getSubscribers(service));
    }
    
    @Override
    public Collection<Subscriber> getFuzzySubscribers(String namespaceId, String serviceName) {
        return new HashSet<>(namingSubscriberServiceV2.getFuzzySubscribers(namespaceId, serviceName));
    }
    
    @Override
    public Collection<Subscriber> getFuzzySubscribers(Service service) {
        return new HashSet<>(namingSubscriberServiceV2.getFuzzySubscribers(service));
    }
}
