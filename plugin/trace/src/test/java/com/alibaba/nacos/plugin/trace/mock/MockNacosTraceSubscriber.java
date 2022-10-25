/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.trace.mock;

import com.alibaba.nacos.common.trace.event.TraceEvent;
import com.alibaba.nacos.plugin.trace.spi.NacosTraceSubscriber;

import java.util.List;

public class MockNacosTraceSubscriber implements NacosTraceSubscriber {
    
    @Override
    public String getName() {
        return "trace-plugin-mock";
    }
    
    @Override
    public void onEvent(TraceEvent event) {
    }
    
    @Override
    public List<Class<? extends TraceEvent>> subscribeTypes() {
        return null;
    }
}
