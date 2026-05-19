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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigDetailServiceTest {
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    private MockedStatic<EnvUtil> envUtilMockedStatic;
    
    private ConfigDetailService configDetailService;
    
    @BeforeEach
    void setUp() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(anyString(), anyString()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        configDetailService = new ConfigDetailService(configInfoPersistService);
    }
    
    @AfterEach
    void tearDown() {
        envUtilMockedStatic.close();
    }
    
    @Test
    void testFindConfigInfoPageBlur() throws Exception {
        Page<ConfigInfo> mockPage = new Page<>();
        mockPage.setTotalCount(1);
        when(configInfoPersistService.findConfigInfoLike4Page(anyInt(), anyInt(),
            anyString(), anyString(), anyString(), any()))
            .thenReturn(mockPage);
        
        Map<String, Object> advanceInfo = new HashMap<>();
        Page<ConfigInfo> result = configDetailService.findConfigInfoPage(
            Constants.CONFIG_SEARCH_BLUR, 1, 10, "d", "g", "ns", advanceInfo);
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
    }
    
    @Test
    void testFindConfigInfoPageAccurate() throws Exception {
        Page<ConfigInfo> mockPage = new Page<>();
        mockPage.setTotalCount(5);
        when(configInfoPersistService.findConfigInfo4Page(anyInt(), anyInt(),
            anyString(), anyString(), anyString(), any()))
            .thenReturn(mockPage);
        
        Map<String, Object> advanceInfo = new HashMap<>();
        Page<ConfigInfo> result = configDetailService.findConfigInfoPage(
            Constants.CONFIG_SEARCH_ACCURATE, 1, 10, "d", "g", "ns", advanceInfo);
        assertNotNull(result);
        assertEquals(5, result.getTotalCount());
    }
    
    @Test
    void testFindConfigInfoPageTimeout() {
        Map<String, Object> advanceInfo = new HashMap<>();
        ConfigDetailService.setWaitTimeout(1L);
        try {
            assertThrows(NacosRuntimeException.class,
                () -> configDetailService.findConfigInfoPage(
                    Constants.CONFIG_SEARCH_BLUR, 1, 10, "d", "g", "ns",
                    advanceInfo));
        } finally {
            ConfigDetailService.setWaitTimeout(8000L);
        }
    }
    
    @Test
    void testFindConfigInfoPageWhenQueueFullThrowsException() {
        BlockingQueue<ConfigDetailService.SearchEvent> queue = mockSearchEventQueue();
        when(queue.offer(any())).thenReturn(false);
        ReflectionTestUtils.setField(configDetailService, "eventLinkedBlockingQueue", queue);
        
        assertThrows(NacosRuntimeException.class,
            () -> configDetailService.findConfigInfoPage(Constants.CONFIG_SEARCH_BLUR, 1, 10,
                "d", "g", "ns", new HashMap<>()));
    }
    
    @Test
    void testFindConfigInfoPageWhenInterruptedThrowsException() {
        BlockingQueue<ConfigDetailService.SearchEvent> queue = mockSearchEventQueue();
        when(queue.offer(any())).thenReturn(true);
        ReflectionTestUtils.setField(configDetailService, "eventLinkedBlockingQueue", queue);
        
        Thread.currentThread().interrupt();
        try {
            assertThrows(NacosRuntimeException.class,
                () -> configDetailService.findConfigInfoPage(Constants.CONFIG_SEARCH_BLUR, 1, 10,
                    "d", "g", "ns", new HashMap<>()));
        } finally {
            Thread.interrupted();
        }
    }
    
    @Test
    void testFindConfigInfoPageWhenNoResponseThrowsException() {
        BlockingQueue<ConfigDetailService.SearchEvent> queue = mockSearchEventQueue();
        when(queue.offer(any())).thenReturn(true);
        ReflectionTestUtils.setField(configDetailService, "eventLinkedBlockingQueue", queue);
        ConfigDetailService.setWaitTimeout(1L);
        
        try {
            assertThrows(NacosRuntimeException.class,
                () -> configDetailService.findConfigInfoPage(Constants.CONFIG_SEARCH_BLUR, 1, 10,
                    "d", "g", "ns", new HashMap<>()));
        } finally {
            ConfigDetailService.setWaitTimeout(8000L);
        }
    }
    
    @Test
    void testFindConfigInfoPageWhenSearchWorkerCatchesException() throws Exception {
        when(configInfoPersistService.findConfigInfoLike4Page(anyInt(), anyInt(), anyString(),
            anyString(), anyString(), any())).thenThrow(new RuntimeException("search failed"));
        ConfigDetailService.setWaitTimeout(50L);
        
        try {
            assertThrows(NacosRuntimeException.class,
                () -> configDetailService.findConfigInfoPage(Constants.CONFIG_SEARCH_BLUR, 1, 10,
                    "d", "g", "ns", new HashMap<>()));
        } finally {
            ConfigDetailService.setWaitTimeout(8000L);
        }
    }
    
    @Test
    void testGettersAndSetters() {
        ConfigDetailService.setMaxCapacity(8);
        assertEquals(8, ConfigDetailService.getMaxCapacity());
        ConfigDetailService.setMaxCapacity(4);
        
        ConfigDetailService.setWaitTimeout(5000L);
        assertEquals(5000L, ConfigDetailService.getWaitTimeout());
        ConfigDetailService.setWaitTimeout(8000L);
        
        ConfigDetailService.setMaxThread(4);
        assertEquals(4, ConfigDetailService.getMaxThread());
        ConfigDetailService.setMaxThread(2);
    }
    
    @Test
    void testSearchEventGetters() {
        Map<String, Object> info = new HashMap<>();
        info.put("key", "value");
        ConfigDetailService.SearchEvent event =
            new ConfigDetailService.SearchEvent("blur", 1, 10, "d", "g", "ns",
                info);
        assertEquals("blur", event.getType());
        assertEquals(1, event.getPageNo());
        assertEquals(10, event.getPageSize());
        assertEquals("d", event.getDataId());
        assertEquals("g", event.getGroup());
        assertEquals("ns", event.getTenant());
        assertEquals(info, event.getConfigAdvanceInfo());
    }
    
    @Test
    void testSearchEventResponseGetterSetter() {
        ConfigDetailService.SearchEvent event = new ConfigDetailService.SearchEvent();
        Page<ConfigInfo> page = new Page<>();
        event.setResponse(page);
        assertEquals(page, event.getResponse());
    }
    
    @SuppressWarnings("unchecked")
    private BlockingQueue<ConfigDetailService.SearchEvent> mockSearchEventQueue() {
        return Mockito.mock(BlockingQueue.class);
    }
}
