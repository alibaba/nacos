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

package com.alibaba.nacos.api.ai.model.skills;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill subscription document for one subscriber in one namespace.
 *
 * @author nacos
 */
public class SkillSubscriptionDocument {
    
    private int schemaVersion = 1;
    
    private String namespaceId;
    
    private String subscriber;
    
    private String groupId;
    
    private String dataId;
    
    private List<SkillSubscription> subscriptions = new ArrayList<>();
    
    public int getSchemaVersion() {
        return schemaVersion;
    }
    
    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getSubscriber() {
        return subscriber;
    }
    
    public void setSubscriber(String subscriber) {
        this.subscriber = subscriber;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public String getDataId() {
        return dataId;
    }
    
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    public List<SkillSubscription> getSubscriptions() {
        return subscriptions;
    }
    
    public void setSubscriptions(List<SkillSubscription> subscriptions) {
        this.subscriptions = subscriptions;
    }
}
