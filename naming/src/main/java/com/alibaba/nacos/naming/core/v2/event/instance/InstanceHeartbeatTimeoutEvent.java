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

package com.alibaba.nacos.naming.core.v2.event.instance;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;

/**
 * Instance heart beat timeout event.
 *
 * @author JackSun-Developer
 */
public class InstanceHeartbeatTimeoutEvent extends Event {
    
    private HealthCheckInstancePublishInfo healthCheckInstancePublishInfo;
    
    public InstanceHeartbeatTimeoutEvent(HealthCheckInstancePublishInfo healthCheckInstancePublishInfo) {
        this.healthCheckInstancePublishInfo = healthCheckInstancePublishInfo;
    }
    
    public HealthCheckInstancePublishInfo getHealthCheckInstancePublishInfo() {
        return healthCheckInstancePublishInfo;
    }
    
    public void setHealthCheckInstancePublishInfo(HealthCheckInstancePublishInfo healthCheckInstancePublishInfo) {
        this.healthCheckInstancePublishInfo = healthCheckInstancePublishInfo;
    }
}
