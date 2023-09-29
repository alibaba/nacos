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
package com.alibaba.nacos.client.naming.backups;

import java.util.Set;


/**
 * Failover switch.
 *
 * @author zongkang.guo
 */
public class FailoverSwitch {

    /**
     * Failover switch enable.
     */
    private boolean enabled;

    /**
     * Failover service name.
     */
    private Set<String> serviceNames;

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getServiceNames() {
        return serviceNames;
    }

    public void setServiceNames(Set<String> serviceNames) {
        this.serviceNames = serviceNames;
    }

    public FailoverSwitch(boolean enabled) {
        this.enabled = enabled;
    }

    public FailoverSwitch(boolean enabled, Set<String> serviceNames) {
        this.enabled = enabled;
        this.serviceNames = serviceNames;
    }
}
