/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.test.sdk.config;

import com.alibaba.nacos.api.config.ConfigQueryResult;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Java SDK {@link ConfigService}.
 *
 * <p>The full scenario matrix and remaining gaps are recorded in
 * {@code test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: publish, query, query-with-result, CAS update, and remove config
 *     through the public Java SDK factory; standalone listener registration receives later
 *     changes.</li>
 *     <li>Boundary/validation: blank group uses the default group, missing config returns
 *     {@code null}, missing query result has an empty result shape, bad CAS md5 is rejected,
 *     empty CAS md5 behaves as normal publish, missing removal is idempotent, and missing
 *     identity/content fields throw {@link NacosException}.</li>
 *     <li>Error handling: invalid config type is mapped to a failed SDK publish result and does
 *     not create data.</li>
 *     <li>Listener/error handling: {@code getConfigAndSignListener} returns the current value,
 *     delivers later updates, standalone listener receives updates, listener removal stops later
 *     callbacks, and listener cleanup plus SDK shutdown are safe.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigServiceJavaSdkITCase extends JavaSdkBaseITCase {
    
    @Test
    public void testPublishQueryCasAndRemoveConfig() throws Exception {
        ConfigService configService = createConfigService();
        String dataId = randomDataId("lifecycle");
        String group = randomGroup("config");
        String firstContent = "sdk.config.first=true";
        String secondContent = "sdk.config.second=true";
        addCleanup(() -> configService.removeConfig(dataId, group));
        
        assertTrue(configService.publishConfig(dataId, group, firstContent, ConfigType.TEXT.getType()));
        assertEquals(firstContent, configService.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
        ConfigQueryResult queryResult = configService.getConfigWithResult(dataId, group, DEFAULT_TIMEOUT_MS);
        assertEquals(firstContent, queryResult.getContent());
        assertNotNull(queryResult.getMd5(), queryResult.toString());
        
        assertFalse(configService.publishConfigCas(dataId, group, "bad-cas-content", "bad-md5",
                ConfigType.TEXT.getType()));
        assertEquals(firstContent, configService.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
        assertTrue(configService.publishConfigCas(dataId, group, secondContent, queryResult.getMd5(),
                ConfigType.TEXT.getType()));
        assertEquals(secondContent, configService.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
        
        assertTrue(configService.removeConfig(dataId, group));
        waitUntil("removed config should be absent", () -> null == configService.getConfig(dataId, group,
                DEFAULT_TIMEOUT_MS));
        assertNull(configService.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
    }
    
    @Test
    public void testMissingConfigResultAndRemoveAreEmptyAndIdempotent() throws Exception {
        ConfigService configService = createConfigService();
        String dataId = randomDataId("missing-result");
        String group = randomGroup("config");
        
        assertNull(configService.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
        ConfigQueryResult queryResult = configService.getConfigWithResult(dataId, group,
                DEFAULT_TIMEOUT_MS);
        assertNotNull(queryResult, "missing config should still return a result object");
        assertNull(queryResult.getContent(), queryResult.toString());
        assertNull(queryResult.getMd5(), queryResult.toString());
        assertNull(queryResult.getConfigType(), queryResult.toString());
        
        assertTrue(configService.removeConfig(dataId, group),
                "server-side remove of an absent config is idempotent");
        assertNull(configService.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
    }
    
    @Test
    public void testCasBoundaryForMissingAndEmptyMd5() throws Exception {
        ConfigService configService = createConfigService();
        String missingDataId = randomDataId("missing-cas");
        String emptyMd5DataId = randomDataId("empty-cas");
        String group = randomGroup("config");
        addCleanup(() -> configService.removeConfig(missingDataId, group));
        addCleanup(() -> configService.removeConfig(emptyMd5DataId, group));
        
        assertFalse(configService.publishConfigCas(missingDataId, group, "missing.cas",
                "missing-md5", ConfigType.TEXT.getType()));
        assertNull(configService.getConfig(missingDataId, group, DEFAULT_TIMEOUT_MS));
        
        assertTrue(configService.publishConfigCas(emptyMd5DataId, group, "empty.md5.cas", "",
                ConfigType.TEXT.getType()));
        assertEquals("empty.md5.cas", configService.getConfig(emptyMd5DataId, group,
                DEFAULT_TIMEOUT_MS));
    }
    
    @Test
    public void testGetConfigAndSignListenerReceivesUpdates() throws Exception {
        ConfigService configService = createConfigService();
        String dataId = randomDataId("listener");
        String group = randomGroup("config");
        String firstContent = "sdk.listener.first=true";
        String secondContent = "sdk.listener.second=true";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        Listener listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }
            
            @Override
            public void receiveConfigInfo(String configInfo) {
                if (secondContent.equals(configInfo)) {
                    received.set(configInfo);
                    latch.countDown();
                }
            }
        };
        addCleanup(() -> configService.removeListener(dataId, group, listener));
        addCleanup(() -> configService.removeConfig(dataId, group));
        
        assertTrue(configService.publishConfig(dataId, group, firstContent));
        assertEquals(firstContent, configService.getConfigAndSignListener(dataId, group, DEFAULT_TIMEOUT_MS,
                listener));
        assertTrue(configService.publishConfig(dataId, group, secondContent));
        
        assertTrue(latch.await(10, TimeUnit.SECONDS), "listener should receive updated config");
        assertEquals(secondContent, received.get());
    }
    
    @Test
    public void testAddListenerReceivesPublishedUpdate() throws Exception {
        ConfigService configService = createConfigService();
        String dataId = randomDataId("add-listener");
        String group = randomGroup("config");
        String content = "sdk.listener.add=true";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        Listener listener = listenerForContent(content, latch, received);
        addCleanup(() -> configService.removeListener(dataId, group, listener));
        addCleanup(() -> configService.removeConfig(dataId, group));
        
        configService.addListener(dataId, group, listener);
        assertTrue(configService.publishConfig(dataId, group, content));
        
        assertTrue(latch.await(10, TimeUnit.SECONDS), "standalone listener should receive update");
        assertEquals(content, received.get());
    }
    
    @Test
    public void testRemoveListenerStopsLaterCallbacks() throws Exception {
        ConfigService configService = createConfigService();
        String dataId = randomDataId("remove-listener");
        String group = randomGroup("config");
        String firstContent = "sdk.listener.before-remove=true";
        String secondContent = "sdk.listener.after-remove=true";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        Listener listener = listenerForContent(secondContent, latch, received);
        addCleanup(() -> configService.removeListener(dataId, group, listener));
        addCleanup(() -> configService.removeConfig(dataId, group));
        
        assertTrue(configService.publishConfig(dataId, group, firstContent));
        configService.addListener(dataId, group, listener);
        configService.removeListener(dataId, group, listener);
        assertTrue(configService.publishConfig(dataId, group, secondContent));
        
        assertFalse(latch.await(2, TimeUnit.SECONDS),
                "removed listener should not receive later update");
        assertNull(received.get());
        assertEquals(secondContent, configService.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
    }
    
    @Test
    public void testConfigValidationAndDefaultGroupBoundary() throws Exception {
        ConfigService configService = createConfigService();
        String group = randomGroup("invalid");
        String defaultGroupDataId = randomDataId("default-group");
        String invalidTypeDataId = randomDataId("invalid-type");
        addCleanup(() -> configService.removeConfig(defaultGroupDataId, Constants.DEFAULT_GROUP));
        addCleanup(() -> configService.removeConfig(invalidTypeDataId, group));
        
        NacosException missingDataId = assertThrows(NacosException.class,
                () -> configService.getConfig("", group, DEFAULT_TIMEOUT_MS));
        assertEquals(NacosException.CLIENT_INVALID_PARAM, missingDataId.getErrCode(), missingDataId.toString());
        
        NacosException missingContent = assertThrows(NacosException.class,
                () -> configService.publishConfig(randomDataId("invalid"), group, ""));
        assertEquals(NacosException.CLIENT_INVALID_PARAM, missingContent.getErrCode(), missingContent.toString());
        
        NacosException invalidGroup = assertThrows(NacosException.class,
                () -> configService.getConfig(randomDataId("invalid"), "bad/group", DEFAULT_TIMEOUT_MS));
        assertEquals(NacosException.CLIENT_INVALID_PARAM, invalidGroup.getErrCode(), invalidGroup.toString());
        
        assertTrue(configService.publishConfig(defaultGroupDataId, "", "default.group.boundary"));
        assertEquals("default.group.boundary", configService.getConfig(defaultGroupDataId,
                Constants.DEFAULT_GROUP, DEFAULT_TIMEOUT_MS));
        
        assertFalse(configService.publishConfig(invalidTypeDataId, group, "content", "bad-type"));
        assertNull(configService.getConfig(invalidTypeDataId, group, DEFAULT_TIMEOUT_MS));
    }
    
    private Listener listenerForContent(String expectedContent, CountDownLatch latch,
            AtomicReference<String> received) {
        return new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }
            
            @Override
            public void receiveConfigInfo(String configInfo) {
                if (expectedContent.equals(configInfo)) {
                    received.set(configInfo);
                    latch.countDown();
                }
            }
        };
    }
}
