/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.config.model;

import java.io.Serializable;

/**
 * Nacos configuration cloned information.
 *
 * @author xiweng.yy
 */
public class ConfigCloneInfo implements Serializable {
    
    private static final long serialVersionUID = -53761233218121703L;
    
    /**
     * The id of need to be cloned configuration, which is the actual storage id not data id. Get from {@link ConfigBasicInfo#getId()}.
     */
    private Long configId;
    
    /**
     * The new group name of configuration after cloned. Optional, if not set, will use the original group name.
     */
    private String targetGroupName;
    
    /**
     * The new data id of configuration after cloned. Optional, if not set, will use the original group name.
     */
    private String targetDataId;
    
    public Long getConfigId() {
        return configId;
    }
    
    public void setConfigId(Long configId) {
        this.configId = configId;
    }
    
    public String getTargetGroupName() {
        return targetGroupName;
    }
    
    public void setTargetGroupName(String targetGroupName) {
        this.targetGroupName = targetGroupName;
    }
    
    public String getTargetDataId() {
        return targetDataId;
    }
    
    public void setTargetDataId(String targetDataId) {
        this.targetDataId = targetDataId;
    }
}
