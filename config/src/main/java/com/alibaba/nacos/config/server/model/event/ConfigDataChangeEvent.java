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
import org.apache.commons.lang3.StringUtils;

/**
 * ConfigDataChangeEvent.
 *
 * @author Nacos
 */
public class ConfigDataChangeEvent extends Event {
    
    public final boolean isBeta;
    
    public final String dataId;
    
    public final String group;
    
    public final String tenant;
    
    public final String tag;
    
    public final long lastModifiedTs;
    
    public ConfigDataChangeEvent(String dataId, String group, long gmtModified) {
        this(false, dataId, group, gmtModified);
    }
    
    public ConfigDataChangeEvent(boolean isBeta, String dataId, String group, String tenant, long gmtModified) {
        if (null == dataId || null == group) {
            throw new IllegalArgumentException("dataId is null or group is null");
        }
        this.isBeta = isBeta;
        this.dataId = dataId;
        this.group = group;
        this.tenant = tenant;
        this.tag = null;
        this.lastModifiedTs = gmtModified;
    }
    
    public ConfigDataChangeEvent(boolean isBeta, String dataId, String group, long gmtModified) {
        this(isBeta, dataId, group, StringUtils.EMPTY, gmtModified);
    }
    
    public ConfigDataChangeEvent(boolean isBeta, String dataId, String group, String tenant, String tag,
            long gmtModified) {
        if (null == dataId || null == group) {
            throw new IllegalArgumentException("dataId is null or group is null");
        }
        this.isBeta = isBeta;
        this.dataId = dataId;
        this.group = group;
        this.tenant = tenant;
        this.tag = tag;
        this.lastModifiedTs = gmtModified;
    }
    
}
