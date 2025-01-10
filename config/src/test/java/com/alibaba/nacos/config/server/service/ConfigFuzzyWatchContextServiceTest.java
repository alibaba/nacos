/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;

import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.ADD_CONFIG;
import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.DELETE_CONFIG;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import java.util.Set;

@ExtendWith(SpringExtension.class)
public class ConfigFuzzyWatchContextServiceTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic ;
    
    @BeforeEach
    public void before(){
        envUtilMockedStatic = Mockito.mockStatic(
                EnvUtil.class);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq( "nacos.config.cache.type"),anyString())).thenReturn("nacos");
    }
    
    @AfterEach
    public void after(){
        envUtilMockedStatic.close();
    }
    
    @Test
    public void testTrimFuzzyWatchContext() throws NacosException {
        
        ConfigCacheService.dump("data124","group","12345","content",System.currentTimeMillis(),null,null);
        ConfigFuzzyWatchContextService configFuzzyWatchContextService=new ConfigFuzzyWatchContextService();
        String groupKey = GroupKey.getKeyTenant("data124", "group", "12345");
        configFuzzyWatchContextService.syncGroupKeyContext(groupKey,ADD_CONFIG);
        //init
        String collectionId="id";
        String groupKeyPattern= FuzzyGroupKeyPattern.generatePattern("data*","group","12345");
        
        configFuzzyWatchContextService.addFuzzyWatch(groupKeyPattern,collectionId);
        
        //test
        Set<String> matchedClients = configFuzzyWatchContextService.getMatchedClients(groupKey);
        Assertions.assertTrue(matchedClients.size()==1);
    
        Set<String> notMatchedClients = configFuzzyWatchContextService.getMatchedClients(
                GroupKey.getKeyTenant("da124", "group", "12345"));
        Assertions.assertTrue(notMatchedClients.size()==0);
    
        Set<String> matchedGroupKeys = configFuzzyWatchContextService.matchGroupKeys(groupKeyPattern);
    
        Assertions.assertTrue(matchedGroupKeys.size()>0);
        Assertions.assertTrue(matchedGroupKeys.contains(groupKey));
        
        // remove connection is watch
        configFuzzyWatchContextService.clearFuzzyWatchContext(collectionId);
        
        //trim once,  matchedClients2 is empty,matchedGroupKeys2 is not empty
        configFuzzyWatchContextService.trimFuzzyWatchContext();
        Set<String> matchedClients2 = configFuzzyWatchContextService.getMatchedClients(groupKey);
        Assertions.assertTrue(matchedClients2!=null&&matchedClients2.isEmpty());
    
        Set<String> matchedGroupKeys2 = configFuzzyWatchContextService.matchGroupKeys(groupKeyPattern);
        Assertions.assertTrue(matchedGroupKeys2!=null&&matchedGroupKeys2.contains(groupKey));
        
        //trim twice, matchedGroupKeys2 is  empty
        configFuzzyWatchContextService.trimFuzzyWatchContext();
        Set<String> matchedGroupKeys3 = configFuzzyWatchContextService.matchGroupKeys(groupKeyPattern);
        Assertions.assertTrue(matchedGroupKeys3==null);
    }
    
    @Test
    public void testSyncGroupKeyContext() throws NacosException {
        ConfigFuzzyWatchContextService configFuzzyWatchContextService=new ConfigFuzzyWatchContextService();
    
        //init
        String collectionId="id";
        String groupKeyPattern= FuzzyGroupKeyPattern.generatePattern("data*","group","12345");
        configFuzzyWatchContextService.addFuzzyWatch(groupKeyPattern,collectionId);
        String keyTenant = GroupKey.getKeyTenant("data1245", "group", "12345");
        boolean needNotify1=configFuzzyWatchContextService.syncGroupKeyContext(keyTenant,ADD_CONFIG);
        Assertions.assertTrue(needNotify1);
        boolean needNotify2=configFuzzyWatchContextService.syncGroupKeyContext(keyTenant,ADD_CONFIG);
        Assertions.assertFalse(needNotify2);
    
        boolean needNotify3=configFuzzyWatchContextService.syncGroupKeyContext(keyTenant,DELETE_CONFIG);
        Assertions.assertTrue(needNotify3);
    
    
        boolean needNotify4=configFuzzyWatchContextService.syncGroupKeyContext(keyTenant,DELETE_CONFIG);
        Assertions.assertFalse(needNotify4);
    
    }
    
    @Test
    public void testFuzzyWatch() throws NacosException {
        ConfigFuzzyWatchContextService configFuzzyWatchContextService=new ConfigFuzzyWatchContextService();
        
        //init
        String collectionId="id";
        String groupKeyPattern= FuzzyGroupKeyPattern.generatePattern("data*","group","12345");
        configFuzzyWatchContextService.addFuzzyWatch(groupKeyPattern,collectionId);
        String groupKey = GroupKey.getKeyTenant("data1245", "group", "12345");
        
        boolean needNotify=configFuzzyWatchContextService.syncGroupKeyContext(groupKey,ADD_CONFIG);
        Assertions.assertTrue(needNotify);
        
        Set<String> matchedClients1 = configFuzzyWatchContextService.getMatchedClients(groupKey);
        Assertions.assertTrue(matchedClients1.contains(collectionId));
        
        configFuzzyWatchContextService.removeFuzzyListen(groupKeyPattern,collectionId);
        Set<String> matchedClients = configFuzzyWatchContextService.getMatchedClients(groupKey);
    
        Assertions.assertTrue(CollectionUtils.isEmpty(matchedClients));
        
    }
    
    @Test
    public void testFuzzyWatchOverLimit() throws NacosException {
        ConfigFuzzyWatchContextService configFuzzyWatchContextService=new ConfigFuzzyWatchContextService();
        
        //init
        String collectionId="id";
        String groupKeyPattern= FuzzyGroupKeyPattern.generatePattern("data*","group","12345");
        configFuzzyWatchContextService.addFuzzyWatch(groupKeyPattern,collectionId);
        String groupKey = GroupKey.getKeyTenant("data1245", "group", "12345");
        boolean needNotify=configFuzzyWatchContextService.syncGroupKeyContext(groupKey,ADD_CONFIG);
        
        Assertions.assertTrue(needNotify);
        
        configFuzzyWatchContextService.removeFuzzyListen(groupKeyPattern,collectionId);
        Set<String> matchedClients = configFuzzyWatchContextService.getMatchedClients(groupKey);
        
        Assertions.assertTrue(CollectionUtils.isEmpty(matchedClients));
        
    }
}
