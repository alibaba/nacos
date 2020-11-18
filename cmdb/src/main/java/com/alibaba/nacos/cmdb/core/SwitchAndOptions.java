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

package com.alibaba.nacos.cmdb.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Switch and options.
 *
 * @author nkorange
 * @since 0.7.0
 */
@Component
public class SwitchAndOptions {
    
    @Value("${nacos.cmdb.dumpTaskInterval:3600}")
    private int dumpTaskInterval;
    
    @Value("${nacos.cmdb.eventTaskInterval:10}")
    private int eventTaskInterval;
    
    @Value("${nacos.cmdb.labelTaskInterval:300}")
    private int labelTaskInterval;
    
    @Value("${nacos.cmdb.loadDataAtStart:false}")
    private boolean loadDataAtStart;
    
    public int getDumpTaskInterval() {
        return dumpTaskInterval;
    }
    
    public int getEventTaskInterval() {
        return eventTaskInterval;
    }
    
    public int getLabelTaskInterval() {
        return labelTaskInterval;
    }
    
    public boolean isLoadDataAtStart() {
        return loadDataAtStart;
    }
}
