/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.api.ai.model.skills.SkillSubscriptionDocument;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * Storage abstraction for skill subscriptions.
 *
 * @author nacos
 */
public interface SkillSubscriptionStorage {
    
    /**
     * Query one subscriber's skill subscription document in a namespace.
     *
     * @param namespaceId namespace ID
     * @param subscriber  subscriber identity
     * @return subscription document, empty when absent
     * @throws NacosException if query failed
     */
    SkillSubscriptionDocument get(String namespaceId, String subscriber) throws NacosException;
    
    /**
     * Save one subscriber's skill subscription document in a namespace.
     *
     * @param namespaceId namespace ID
     * @param subscriber  subscriber identity
     * @param document    subscription document
     * @throws NacosException if save failed
     */
    void save(String namespaceId, String subscriber, SkillSubscriptionDocument document)
        throws NacosException;
}
