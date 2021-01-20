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

import java.util.List;

/**
 * LocalDataChangeEvent.
 *
 * @author Nacos
 */
public class LocalDataChangeEvent extends Event {
    
    public final String groupKey;
    
    public final boolean isBeta;
    
    public final List<String> betaIps;
    
    public final String tag;
    
    public LocalDataChangeEvent(String groupKey) {
        this.groupKey = groupKey;
        this.isBeta = false;
        this.betaIps = null;
        this.tag = null;
    }
    
    public LocalDataChangeEvent(String groupKey, boolean isBeta, List<String> betaIps) {
        this.groupKey = groupKey;
        this.isBeta = isBeta;
        this.betaIps = betaIps;
        this.tag = null;
    }
    
    public LocalDataChangeEvent(String groupKey, boolean isBeta, List<String> betaIps, String tag) {
        this.groupKey = groupKey;
        this.isBeta = isBeta;
        this.betaIps = betaIps;
        this.tag = tag;
    }
}
