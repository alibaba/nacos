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

package com.alibaba.nacos.naming.push;

import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.index.NamingFuzzyWatchContextService;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.push.v2.task.FuzzyWatchChangeNotifyTask;
import com.alibaba.nacos.naming.push.v2.task.FuzzyWatchPushDelayTaskEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static com.alibaba.nacos.api.common.Constants.ServiceChangedType.ADD_SERVICE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NamingFuzzyWatchChangeNotifierTest {
    
    @Mock
    private NamingFuzzyWatchContextService namingFuzzyWatchContextService;
    
    @Mock
    private FuzzyWatchPushDelayTaskEngine fuzzyWatchPushDelayTaskEngine;
    
    NamingFuzzyWatchChangeNotifier namingFuzzyWatchChangeNotifier;
    
    @BeforeEach
    void before() {
        namingFuzzyWatchChangeNotifier = new NamingFuzzyWatchChangeNotifier(namingFuzzyWatchContextService,
                fuzzyWatchPushDelayTaskEngine);
    }
    
    @AfterEach
    void after() {
    }
    
    @Test
    void testServiceChangedEvent() {
        Service service = Service.newService("namespace12", "group", "service12345");
        
        when(namingFuzzyWatchContextService.syncServiceContext(eq(service), eq(ADD_SERVICE))).thenReturn(true);
        
        Set set = new HashSet();
        set.add("2345123");
        set.add("23453");
        set.add("234535");
        
        when(namingFuzzyWatchContextService.getFuzzyWatchedClients(eq(service))).thenReturn(set);
    
        ServiceEvent.ServiceChangedEvent serviceChangedEvent = new ServiceEvent.ServiceChangedEvent(service,
                ADD_SERVICE);
        namingFuzzyWatchChangeNotifier.onEvent(serviceChangedEvent);
        
        verify(fuzzyWatchPushDelayTaskEngine, times(set.size())).addTask(anyString(),
                any(FuzzyWatchChangeNotifyTask.class));
    }
}
