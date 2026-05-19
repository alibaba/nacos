/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.config.server.model.event.ConfigDumpEvent;
import com.alibaba.nacos.config.server.service.ClientIpWhiteList;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.SwitchService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DumpConfigHandlerTest {
    
    private MockedStatic<ConfigCacheService> configCacheServiceMockedStatic;
    
    private MockedStatic<EnvUtil> envUtilMockedStatic;
    
    @BeforeEach
    void setUp() {
        configCacheServiceMockedStatic =
            Mockito.mockStatic(ConfigCacheService.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(
            "nacos.config.cache.type", "nacos"))
            .thenReturn("nacos");
    }
    
    @AfterEach
    void tearDown() {
        configCacheServiceMockedStatic.close();
        envUtilMockedStatic.close();
        ClientIpWhiteList.load("");
        SwitchService.load("");
    }
    
    @Test
    void testConfigDumpGrayAdd() {
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.dumpGray(
            "d", "g", "ns", "gray1", "rule", "content", 100L, "edk"))
            .thenReturn(true);
        
        ConfigDumpEvent event = ConfigDumpEvent.builder()
            .dataId("d").group("g").namespaceId("ns")
            .grayName("gray1").grayRule("rule")
            .content("content").lastModifiedTs(100L)
            .encryptedDataKey("edk").handleIp("1.1.1.1")
            .remove(false).build();
        
        assertTrue(DumpConfigHandler.configDump(event));
    }
    
    @Test
    void testConfigDumpGrayRemove() {
        configCacheServiceMockedStatic
            .when(() -> ConfigCacheService.removeGray("d", "g", "ns", "gray1"))
            .thenReturn(true);
        
        ConfigDumpEvent event = ConfigDumpEvent.builder()
            .dataId("d").group("g").namespaceId("ns")
            .grayName("gray1").lastModifiedTs(100L)
            .handleIp("1.1.1.1").remove(true).build();
        
        assertTrue(DumpConfigHandler.configDump(event));
    }
    
    @Test
    void testConfigDumpNormalAdd() {
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.dump(
            "d", "g", "ns", "content", 100L, "text", "edk"))
            .thenReturn(true);
        
        ConfigDumpEvent event = ConfigDumpEvent.builder()
            .dataId("d").group("g").namespaceId("ns")
            .content("content").lastModifiedTs(100L)
            .type("text").encryptedDataKey("edk")
            .handleIp("1.1.1.1").remove(false).build();
        
        assertTrue(DumpConfigHandler.configDump(event));
    }
    
    @Test
    void testConfigDumpNormalRemove() {
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.remove("d", "g", "ns"))
            .thenReturn(true);
        
        ConfigDumpEvent event = ConfigDumpEvent.builder()
            .dataId("d").group("g").namespaceId("ns")
            .lastModifiedTs(100L).handleIp("1.1.1.1")
            .remove(true).build();
        
        assertTrue(DumpConfigHandler.configDump(event));
    }
    
    @Test
    void testConfigDumpNormalAddFailed() {
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.dump(
            "d", "g", "ns", "content", 100L, "text", "edk"))
            .thenReturn(false);
        
        ConfigDumpEvent event = ConfigDumpEvent.builder()
            .dataId("d").group("g").namespaceId("ns")
            .content("content").lastModifiedTs(100L)
            .type("text").encryptedDataKey("edk")
            .handleIp("1.1.1.1").remove(false).build();
        
        assertFalse(DumpConfigHandler.configDump(event));
    }
    
    @Test
    void testConfigDumpClientIpWhiteListMetadata() {
        String content = "{\"isOpen\":true,\"ips\":[\"1.1.1.1\"]}";
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.dump(
            ClientIpWhiteList.CLIENT_IP_WHITELIST_METADATA, "g", "ns", content, 100L, "text",
            "edk")).thenReturn(true);
        
        ConfigDumpEvent event = ConfigDumpEvent.builder()
            .dataId(ClientIpWhiteList.CLIENT_IP_WHITELIST_METADATA).group("g")
            .namespaceId("ns").content(content).lastModifiedTs(100L).type("text")
            .encryptedDataKey("edk").handleIp("1.1.1.1").remove(false).build();
        
        assertTrue(DumpConfigHandler.configDump(event));
        assertTrue(ClientIpWhiteList.isEnableWhitelist());
        assertTrue(ClientIpWhiteList.isLegalClient("1.1.1.1"));
    }
    
    @Test
    void testConfigDumpSwitchMetadata() {
        String content = SwitchService.FIXED_DELAY_TIME + "=1000";
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.dump(
            SwitchService.SWITCH_META_DATA_ID, "g", "ns", content, 100L, "text", "edk"))
            .thenReturn(true);
        
        ConfigDumpEvent event = ConfigDumpEvent.builder()
            .dataId(SwitchService.SWITCH_META_DATA_ID).group("g").namespaceId("ns")
            .content(content).lastModifiedTs(100L).type("text").encryptedDataKey("edk")
            .handleIp("1.1.1.1").remove(false).build();
        
        assertTrue(DumpConfigHandler.configDump(event));
        assertEquals(1000, SwitchService.getSwitchInteger(SwitchService.FIXED_DELAY_TIME, 0));
    }
    
    @Test
    void testOnEvent() {
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.remove("d", "g", "ns"))
            .thenReturn(true);
        
        ConfigDumpEvent event = ConfigDumpEvent.builder()
            .dataId("d").group("g").namespaceId("ns")
            .lastModifiedTs(100L).handleIp("1.1.1.1")
            .remove(true).build();
        
        DumpConfigHandler handler = new DumpConfigHandler();
        handler.onEvent(event);
    }
    
    @Test
    void testSubscribeType() {
        DumpConfigHandler handler = new DumpConfigHandler();
        assertEquals(ConfigDumpEvent.class, handler.subscribeType());
    }
}
