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

package com.alibaba.nacos.ai.event;

import com.alibaba.nacos.common.notify.Event;

/**
 * Best-effort current-fact invalidation for one logical AI resource.
 *
 * <p>The event carries no resource bytes, credentials, or authorization decision. Consumers must
 * re-read the current resource through their normal projection and authorization boundaries.</p>
 *
 * @author Nacos
 */
public class AiResourceChangedEvent extends Event {
    
    private static final long serialVersionUID = 5471214326779182321L;
    
    private final String namespaceId;
    
    private final String resourceType;
    
    private final String resourceName;
    
    private final AiResourceChangeOperation operation;
    
    private final boolean storageChanged;
    
    public AiResourceChangedEvent(String namespaceId, String resourceType, String resourceName,
        AiResourceChangeOperation operation, boolean storageChanged) {
        this.namespaceId = namespaceId;
        this.resourceType = resourceType;
        this.resourceName = resourceName;
        this.operation = operation;
        this.storageChanged = storageChanged;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public String getResourceName() {
        return resourceName;
    }
    
    public AiResourceChangeOperation getOperation() {
        return operation;
    }
    
    public boolean isStorageChanged() {
        return storageChanged;
    }
    
    /**
     * Merge a previous hint for the same logical resource into this newer current-fact hint.
     *
     * @param previous previous hint
     * @return merged hint retaining the newer operation and any storage-change signal
     */
    public AiResourceChangedEvent mergePrevious(AiResourceChangedEvent previous) {
        return previous == null || !previous.storageChanged || storageChanged ? this
            : new AiResourceChangedEvent(namespaceId, resourceType, resourceName, operation, true);
    }
}
