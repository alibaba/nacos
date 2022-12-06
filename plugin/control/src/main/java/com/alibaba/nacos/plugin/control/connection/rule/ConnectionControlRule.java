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

package com.alibaba.nacos.plugin.control.connection.rule;

import java.util.HashSet;
import java.util.Set;

/**
 * connection control rule.
 *
 * @author shiyiyue
 */
public class ConnectionControlRule {
    
    private Set<String> monitorIpList = new HashSet<>();
    
    private int countLimit = -1;
    
    public int getCountLimit() {
        return countLimit;
    }
    
    public void setCountLimit(int countLimit) {
        this.countLimit = countLimit;
    }
    
    public Set<String> getMonitorIpList() {
        return monitorIpList;
    }
    
    public void setMonitorIpList(Set<String> monitorIpList) {
        this.monitorIpList = monitorIpList;
    }
}
