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
import com.alibaba.nacos.config.server.configuration.ConfigCommonConfig;
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

import java.util.Set;

import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.ADD_CONFIG;
import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.DELETE_CONFIG;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_OVER_LIMIT;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class ConfigFuzzyWatchContextServiceTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<ConfigCommonConfig> configCommonConfigMockedStatic;
    
    private static int mocMaxPattern = 5;
    
    private static int mocMaxPatternConfigCount = 10;
    
    /**
     * before.
     */
    @BeforeEach
    public void before() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("nacos.config.cache.type"), anyString()))
                .thenReturn("nacos");
        
        configCommonConfigMockedStatic = Mockito.mockStatic(ConfigCommonConfig.class);
        
        ConfigCommonConfig configCommonConfig = Mockito.mock(ConfigCommonConfig.class);
        when(configCommonConfig.getMaxPatternCount()).thenReturn(mocMaxPattern);
        when(configCommonConfig.getMaxMatchedConfigCount()).thenReturn(mocMaxPatternConfigCount);
        
        configCommonConfigMockedStatic.when(() -> ConfigCommonConfig.getInstance()).thenReturn(configCommonConfig);
    }
    
    @AfterEach
    public void after() {
        envUtilMockedStatic.close();
        configCommonConfigMockedStatic.close();
    }
    
    @Test
    public void testTrimFuzzyWatchContext() throws NacosException {
        
        ConfigFuzzyWatchContextService configFuzzyWatchContextService = new ConfigFuzzyWatchContextService();
        String groupKey = GroupKey.getKeyTenant("data124", "group", "12345");
        //init
        String collectionId = "id";
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("data*", "group", "12345");
        
        configFuzzyWatchContextService.addFuzzyWatch(groupKeyPattern, collectionId);
        configFuzzyWatchContextService.syncGroupKeyContext(groupKey, ADD_CONFIG);
        
        //test
        Set<String> matchedClients = configFuzzyWatchContextService.getMatchedClients(groupKey);
        Assertions.assertTrue(matchedClients.size() == 1);
        
        Set<String> notMatchedClients = configFuzzyWatchContextService.getMatchedClients(
                GroupKey.getKeyTenant("da124", "group", "12345"));
        Assertions.assertTrue(notMatchedClients.size() == 0);
        
        Set<String> matchedGroupKeys = configFuzzyWatchContextService.matchGroupKeys(groupKeyPattern);
        
        Assertions.assertTrue(matchedGroupKeys.size() > 0);
        Assertions.assertTrue(matchedGroupKeys.contains(groupKey));
        
        // remove connection is watch
        configFuzzyWatchContextService.clearFuzzyWatchContext(collectionId);
        
        //trim once,  matchedClients2 is empty,matchedGroupKeys2 is not empty
        configFuzzyWatchContextService.trimFuzzyWatchContext();
        Set<String> matchedClients2 = configFuzzyWatchContextService.getMatchedClients(groupKey);
        Assertions.assertTrue(matchedClients2 != null && matchedClients2.isEmpty());
        
        Set<String> matchedGroupKeys2 = configFuzzyWatchContextService.matchGroupKeys(groupKeyPattern);
        Assertions.assertTrue(matchedGroupKeys2 != null && matchedGroupKeys2.contains(groupKey));
        
        //trim twice, matchedGroupKeys2 is  empty
        configFuzzyWatchContextService.trimFuzzyWatchContext();
        Set<String> matchedGroupKeys3 = configFuzzyWatchContextService.matchGroupKeys(groupKeyPattern);
        Assertions.assertTrue(matchedGroupKeys3.isEmpty());
    }
    
    @Test
    public void testSyncGroupKeyContext() throws NacosException {
        ConfigFuzzyWatchContextService configFuzzyWatchContextService = new ConfigFuzzyWatchContextService();
        
        //init
        String collectionId = "id";
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("data*", "group", "12345");
        configFuzzyWatchContextService.addFuzzyWatch(groupKeyPattern, collectionId);
        String keyTenant = GroupKey.getKeyTenant("data1245", "group", "12345");
        boolean needNotify1 = configFuzzyWatchContextService.syncGroupKeyContext(keyTenant, ADD_CONFIG);
        Assertions.assertTrue(needNotify1);
        boolean needNotify2 = configFuzzyWatchContextService.syncGroupKeyContext(keyTenant, ADD_CONFIG);
        Assertions.assertFalse(needNotify2);
        
        boolean needNotify3 = configFuzzyWatchContextService.syncGroupKeyContext(keyTenant, DELETE_CONFIG);
        Assertions.assertTrue(needNotify3);
        
        boolean needNotify4 = configFuzzyWatchContextService.syncGroupKeyContext(keyTenant, DELETE_CONFIG);
        Assertions.assertFalse(needNotify4);
        
    }
    
    @Test
    public void testMakeupGroupKeyContext() throws NacosException {
        
        ConfigFuzzyWatchContextService configFuzzyWatchContextService = new ConfigFuzzyWatchContextService();
        
        //init
        String collectionId = "id";
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("data*", "group", "12345");
        configFuzzyWatchContextService.addFuzzyWatch(groupKeyPattern, collectionId);
        
        for (int i = 0; i <= mocMaxPatternConfigCount; i++) {
            String keyTenant = GroupKey.getKeyTenant("data1" + i, "group", "12345");
            boolean needNotify1 = configFuzzyWatchContextService.syncGroupKeyContext(keyTenant, ADD_CONFIG);
            Assertions.assertEquals(i < mocMaxPatternConfigCount ? true : false, needNotify1);
        }
        
        String overLimitKey = GroupKey.getKeyTenant("data1" + mocMaxPatternConfigCount, "group", "12345");
        Assertions.assertFalse(configFuzzyWatchContextService.matchGroupKeys(groupKeyPattern).contains(overLimitKey));
        
        //sync init cache service
        ConfigCacheService.dump("data1" + mocMaxPatternConfigCount, "group", "12345", "content",
                System.currentTimeMillis(), null, null);
        
        String deletedKey = GroupKey.getKeyTenant("data1" + 0, "group", "12345");
        
        configFuzzyWatchContextService.syncGroupKeyContext(deletedKey, DELETE_CONFIG);
        
        Assertions.assertTrue(configFuzzyWatchContextService.matchGroupKeys(groupKeyPattern).contains(overLimitKey));
        
    }
    
    @Test
    public void testInitGroupKeyContext() throws NacosException {
        
        ConfigFuzzyWatchContextService configFuzzyWatchContextService = new ConfigFuzzyWatchContextService();
        String dataIdPrefix = "testinitD";
        // init config
        for (int i = 0; i <= mocMaxPatternConfigCount; i++) {
            ConfigCacheService.dump(dataIdPrefix + i, "group", "12345", "content", System.currentTimeMillis(), null,
                    null);
        }
        
        String collectionId = "id";
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern(dataIdPrefix + "*", "group", "12345");
        
        // test init config
        configFuzzyWatchContextService.addFuzzyWatch(groupKeyPattern, collectionId);
        Assertions.assertEquals(mocMaxPatternConfigCount,
                configFuzzyWatchContextService.matchGroupKeys(groupKeyPattern).size());
        
        for (int i = 1; i < mocMaxPattern; i++) {
            String groupKeyPattern0 = FuzzyGroupKeyPattern.generatePattern(dataIdPrefix + "*" + i, "group", "12345");
            configFuzzyWatchContextService.addFuzzyWatch(groupKeyPattern0, collectionId);
        }
        
        try {
            String groupKeyPatternOver = FuzzyGroupKeyPattern.generatePattern(dataIdPrefix + "*" + mocMaxPattern,
                    "group", "12345");
            
            configFuzzyWatchContextService.addFuzzyWatch(groupKeyPatternOver, collectionId);
            Assertions.assertTrue(false);
        } catch (NacosException nacosException) {
            Assertions.assertEquals(FUZZY_WATCH_PATTERN_OVER_LIMIT.getCode(), nacosException.getErrCode());
            Assertions.assertEquals(FUZZY_WATCH_PATTERN_OVER_LIMIT.getMsg(), nacosException.getErrMsg());
        }
        
    }
    
    @Test
    public void testFuzzyWatch() throws NacosException {
        ConfigFuzzyWatchContextService configFuzzyWatchContextService = new ConfigFuzzyWatchContextService();
        
        //init
        String collectionId = "id";
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("data*", "group", "12345");
        configFuzzyWatchContextService.addFuzzyWatch(groupKeyPattern, collectionId);
        String groupKey = GroupKey.getKeyTenant("data1245", "group", "12345");
        
        boolean needNotify = configFuzzyWatchContextService.syncGroupKeyContext(groupKey, ADD_CONFIG);
        Assertions.assertTrue(needNotify);
        
        Set<String> matchedClients1 = configFuzzyWatchContextService.getMatchedClients(groupKey);
        Assertions.assertTrue(matchedClients1.contains(collectionId));
        
        configFuzzyWatchContextService.removeFuzzyListen(groupKeyPattern, collectionId);
        Set<String> matchedClients = configFuzzyWatchContextService.getMatchedClients(groupKey);
        
        Assertions.assertTrue(CollectionUtils.isEmpty(matchedClients));
        
    }
    
    @Test
    public void testFuzzyWatchOverLimit() throws NacosException {
        ConfigFuzzyWatchContextService configFuzzyWatchContextService = new ConfigFuzzyWatchContextService();
        
        //init
        String collectionId = "id";
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("data*", "group", "12345");
        configFuzzyWatchContextService.addFuzzyWatch(groupKeyPattern, collectionId);
        String groupKey = GroupKey.getKeyTenant("data1245", "group", "12345");
        boolean needNotify = configFuzzyWatchContextService.syncGroupKeyContext(groupKey, ADD_CONFIG);
        
        Assertions.assertTrue(needNotify);
        
        configFuzzyWatchContextService.removeFuzzyListen(groupKeyPattern, collectionId);
        Set<String> matchedClients = configFuzzyWatchContextService.getMatchedClients(groupKey);
        
        Assertions.assertTrue(CollectionUtils.isEmpty(matchedClients));
        
    }
}
