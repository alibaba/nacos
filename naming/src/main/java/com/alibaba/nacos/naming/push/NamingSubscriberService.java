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

import java.util.Collection;

/**
 * Naming subscriber service.
 *
 * @author xiweng.yy
 */
public interface NamingSubscriberService {
    
    /**
     * Get all push target subscribers for specified service.
     *
     * @param namespaceId namespace id
     * @param serviceName service name
     * @return list of subsribers
     */
    Collection<Subscriber> getSubscribers(String namespaceId, String serviceName);
    
    /**
     * Get all push target subscribers for specified service.
     *
     * @param service {@link Service}
     * @return list of subsribers
     */
    Collection<Subscriber> getSubscribers(Service service);
    
    /**
     * Fuzzy get subscribers. Only support fuzzy serviceName.
     *
     * <p>Warning: This method cost much performance, use less.
     *
     * @param namespaceId namespace id
     * @param serviceName fuzzy serviceName
     * @return list of subsribers
     */
    Collection<Subscriber> getFuzzySubscribers(String namespaceId, String serviceName);
    
    /**
     * Fuzzy get subscribers. Only support fuzzy serviceName.
     *
     * <p>Warning: This method cost much performance, use less.
     *
     * @param service {@link Service}
     * @return list of subsribers
     */
    Collection<Subscriber> getFuzzySubscribers(Service service);
}
