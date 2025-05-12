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

package com.alibaba.nacos.naming.core.v2.index;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static com.alibaba.nacos.api.common.Constants.ServiceChangedType.ADD_SERVICE;
import static com.alibaba.nacos.api.common.Constants.ServiceChangedType.DELETE_SERVICE;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_OVER_LIMIT;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NamingFuzzyWatchContextServiceTest {
    
    NamingFuzzyWatchContextService namingFuzzyWatchContextService;
    
    @Mock
    MockedStatic<GlobalConfig> tMockedStatic;
    
    @Mock
    MockedStatic<ServiceManager> serviceManagerMockedStatic;
    
    @Mock
    ServiceManager serviceManager;
    
    @BeforeEach
    void before() {
        namingFuzzyWatchContextService = new NamingFuzzyWatchContextService();
        
        serviceManagerMockedStatic.when(() -> ServiceManager.getInstance()).thenReturn(serviceManager);
    }
    
    @AfterEach
    void after() {
        tMockedStatic.close();
        serviceManagerMockedStatic.close();
    }
    
    @Test
    void testInitWatchMatchServiceNormal() throws NacosException {
        tMockedStatic.when(() -> GlobalConfig.getMaxPatternCount()).thenReturn(20);
        tMockedStatic.when(() -> GlobalConfig.getMaxMatchedServiceCount()).thenReturn(500);
        
        //mock services
        Set<Service> serviceSet = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Service service = Service.newService("namespace", "group" + i, "service" + i, true);
            serviceSet.add(service);
        }
        serviceSet.add(Service.newService("namespace", "group", "12service", true));
        
        when(serviceManager.getSingletons(eq("namespace"))).thenReturn(serviceSet);
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("service*", "group*", "namespace");
        Set<String> strings = namingFuzzyWatchContextService.initWatchMatchService(groupKeyPattern);
        for (int i = 0; i < 10; i++) {
            Assertions.assertTrue(strings.contains(NamingUtils.getServiceKey("namespace", "group" + i, "service" + i)));
        }
        Assertions.assertFalse(strings.contains(NamingUtils.getServiceKey("namespace", "group", "12service")));
        
    }
    
    @Test
    void testInitWatchMatchServiceOverLoadPatternCount() throws NacosException {
        tMockedStatic.when(() -> GlobalConfig.getMaxPatternCount()).thenReturn(5);
        
        for (int i = 0; i < 10; i++) {
            String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("service*" + i, "group*", "namespace");
            
            if (i < 5) {
                int size = namingFuzzyWatchContextService.initWatchMatchService(groupKeyPattern).size();
                Assertions.assertEquals(0, size);
            } else {
                try {
                    namingFuzzyWatchContextService.initWatchMatchService(groupKeyPattern).size();
                    Assertions.assertFalse(true);
                } catch (Exception nacosException) {
                    Assertions.assertTrue(nacosException instanceof NacosException);
                    Assertions.assertTrue(
                            ((NacosException) nacosException).getErrCode() == FUZZY_WATCH_PATTERN_OVER_LIMIT.getCode());
                }
            }
        }
    }
    
    @Test
    void testInitWatchMatchServiceOverLoadServiceCount() throws NacosException {
        tMockedStatic.when(() -> GlobalConfig.getMaxPatternCount()).thenReturn(20);
        tMockedStatic.when(() -> GlobalConfig.getMaxMatchedServiceCount()).thenReturn(5);
        
        //mock services
        Set<Service> serviceSet = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Service service = Service.newService("namespace", "group" + i, "service" + i, true);
            serviceSet.add(service);
        }
        serviceSet.add(Service.newService("namespace", "group", "12service", true));
        when(serviceManager.getSingletons(eq("namespace"))).thenReturn(serviceSet);
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("service*", "group*", "namespace");
        Set<String> strings = namingFuzzyWatchContextService.initWatchMatchService(groupKeyPattern);
        Assertions.assertTrue(strings.size() == 5);
        Assertions.assertFalse(strings.contains(NamingUtils.getServiceKey("namespace", "group", "12service")));
    }
    
    @Test
    void testSyncServiceContext() throws NacosException {
        tMockedStatic.when(() -> GlobalConfig.getMaxPatternCount()).thenReturn(20);
        tMockedStatic.when(() -> GlobalConfig.getMaxMatchedServiceCount()).thenReturn(500);
        
        //init group key context.
        
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("1service*", "2group*", "3namespace");
        String connectionId = "conn1234";
        namingFuzzyWatchContextService.syncFuzzyWatcherContext(groupKeyPattern, connectionId);
        
        //match service  add
        Service service = Service.newService("3namespace", "2group", "1service", true);
        boolean needNotify = namingFuzzyWatchContextService.syncServiceContext(service, ADD_SERVICE);
        Assertions.assertTrue(needNotify);
        boolean needNotify2 = namingFuzzyWatchContextService.syncServiceContext(service, ADD_SERVICE);
        Assertions.assertFalse(needNotify2);
        
        //check matched client and services
        Set<String> fuzzyWatchedClients = namingFuzzyWatchContextService.getFuzzyWatchedClients(service);
        Assertions.assertTrue(fuzzyWatchedClients.contains(connectionId));
        Set<String> matchServiceKeys = namingFuzzyWatchContextService.matchServiceKeys(groupKeyPattern);
        Assertions.assertTrue(matchServiceKeys.contains(
                NamingUtils.getServiceKey(service.getNamespace(), service.getGroup(), service.getName())));
        
        //not match service add
        Service serviceNotMatch = Service.newService("345namespace", "2group", "1service", true);
        boolean needNotifyNotMatch = namingFuzzyWatchContextService.syncServiceContext(serviceNotMatch, ADD_SERVICE);
        Assertions.assertFalse(needNotifyNotMatch);
        
        //match service  add
        Service serviceDRemove = service;
        boolean needNotifyRemove = namingFuzzyWatchContextService.syncServiceContext(serviceDRemove, DELETE_SERVICE);
        Assertions.assertTrue(needNotifyRemove);
        boolean needNotify2Remove = namingFuzzyWatchContextService.syncServiceContext(serviceDRemove, DELETE_SERVICE);
        Assertions.assertFalse(needNotify2Remove);
        
    }
    
    @Test
    void testMakeupContextOnOverLoad() throws NacosException {
        
        tMockedStatic.when(() -> GlobalConfig.getMaxPatternCount()).thenReturn(20);
        tMockedStatic.when(() -> GlobalConfig.getMaxMatchedServiceCount()).thenReturn(10);
        
        //mock services
        Set<Service> serviceSet = new HashSet<>();
        for (int i = 0; i < 11; i++) {
            Service service = Service.newService("namespace", "group" + i, "service" + i, true);
            serviceSet.add(service);
        }
        serviceSet.add(Service.newService("namespace", "group", "12service", true));
        when(serviceManager.getSingletons(eq("namespace"))).thenReturn(serviceSet);
        
        String connectionId = "connection";
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("service*", "group*", "namespace");
        namingFuzzyWatchContextService.syncFuzzyWatcherContext(groupKeyPattern, connectionId);
        Set<String> matchServiceKeys = namingFuzzyWatchContextService.matchServiceKeys(groupKeyPattern);
        Assertions.assertEquals(10, matchServiceKeys.size());
        
        String serviceKeyToRemove = (String) (matchServiceKeys.toArray()[0]);
        String[] parseServiceKey = NamingUtils.parseServiceKey(serviceKeyToRemove);
        Service serviceToRemove = Service.newService(parseServiceKey[0], parseServiceKey[1], parseServiceKey[2]);
        
        Set<Service> serviceSet2 = new HashSet<>(serviceSet);
        serviceSet2.remove(serviceToRemove);
        when(serviceManager.getSingletons(eq("namespace"))).thenReturn(serviceSet2);
        //delete on over load
        boolean needNotify = namingFuzzyWatchContextService.syncServiceContext(serviceToRemove, DELETE_SERVICE);
        Assertions.assertTrue(needNotify);
        Set<String> matchServiceKeys2 = namingFuzzyWatchContextService.matchServiceKeys(groupKeyPattern);
        Assertions.assertFalse(matchServiceKeys2.contains(serviceKeyToRemove));
        Assertions.assertEquals(10, matchServiceKeys2.size());
        
    }
    
    @Test
    void testTrimContext() throws NacosException {
        tMockedStatic.when(() -> GlobalConfig.getMaxPatternCount()).thenReturn(20);
        tMockedStatic.when(() -> GlobalConfig.getMaxMatchedServiceCount()).thenReturn(5);
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("service*", "group*", "namespace");
        
        //mock services
        Set<Service> serviceSet = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Service service = Service.newService("namespace", "group" + i, "service" + i, true);
            serviceSet.add(service);
        }
        when(serviceManager.getSingletons(eq("namespace"))).thenReturn(serviceSet);
        String connectionId = "connection";
        namingFuzzyWatchContextService.syncFuzzyWatcherContext(groupKeyPattern, connectionId);
        String connectionId2 = "connection22";
        namingFuzzyWatchContextService.syncFuzzyWatcherContext(groupKeyPattern, connectionId2);
        Set<String> matchServiceKeys = namingFuzzyWatchContextService.matchServiceKeys(groupKeyPattern);
        //
        namingFuzzyWatchContextService.trimFuzzyWatchContext();
        Set<String> matchServiceKeys2 = namingFuzzyWatchContextService.matchServiceKeys(groupKeyPattern);
        Assertions.assertEquals(matchServiceKeys, matchServiceKeys2);
        
        namingFuzzyWatchContextService.removeFuzzyWatchContext(groupKeyPattern, connectionId2);
        namingFuzzyWatchContextService.onEvent(
                new ClientOperationEvent.ClientReleaseEvent(new ConnectionBasedClient(connectionId, true, 0L), true));
        namingFuzzyWatchContextService.trimFuzzyWatchContext();
        Set<String> matchServiceKeys3 = namingFuzzyWatchContextService.matchServiceKeys(groupKeyPattern);
        Assertions.assertEquals(matchServiceKeys3, matchServiceKeys2);
        namingFuzzyWatchContextService.trimFuzzyWatchContext();
        Set<String> matchServiceKeys4 = namingFuzzyWatchContextService.matchServiceKeys(groupKeyPattern);
        Assertions.assertEquals(0, matchServiceKeys4.size());
        
    }
}
