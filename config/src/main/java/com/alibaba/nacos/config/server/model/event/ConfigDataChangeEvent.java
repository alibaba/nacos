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

package com.alibaba.nacos.config.server.model.event;

import com.alibaba.nacos.common.notify.Event;

/**
 * ConfigDataChangeEvent.
 *
 * @author Nacos
 */
public class ConfigDataChangeEvent extends Event {
    
    public String dataId;
    
    public String group;
    
    public String tenant;
    
    public String grayName;
    
    public final long lastModifiedTs;
    
    public ConfigDataChangeEvent(String dataId, String group, String tenant, long gmtModified) {
        if (null == dataId || null == group) {
            throw new IllegalArgumentException("dataId is null or group is null");
        }
        this.dataId = dataId;
        this.group = group;
        this.tenant = tenant;
        this.lastModifiedTs = gmtModified;
    }
    
    public ConfigDataChangeEvent(String dataId, String group, String tenant, String grayName, long gmtModified) {
        this(dataId, group, tenant, gmtModified);
        this.grayName = grayName;
    }
    
}
