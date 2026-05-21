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

package com.alibaba.nacos.config.server.service.repository.embedded;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.event.ConfigDumpEvent;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

class EmbeddedConfigDumpApplyHookTest {
    
    private EmbeddedConfigDumpApplyHook hook;
    
    private MockedStatic<NotifyCenter> notifyCenterMockedStatic;
    
    @BeforeEach
    void setUp() {
        notifyCenterMockedStatic = Mockito.mockStatic(NotifyCenter.class);
        hook = new EmbeddedConfigDumpApplyHook();
    }
    
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        notifyCenterMockedStatic.close();
    }
    
    @Test
    void testAfterApplyWithSingleDumpEvent() {
        ConfigDumpEvent event = ConfigDumpEvent.builder()
            .dataId("d").group("g").namespaceId("ns")
            .content("content").build();
        String json = JacksonUtils.toJson(event);
        
        Map<String, String> extendInfo = new HashMap<>();
        extendInfo.put(Constants.EXTEND_INFO_CONFIG_DUMP_EVENT, json);
        
        WriteRequest log = WriteRequest.newBuilder()
            .putAllExtendInfo(extendInfo).build();
        hook.afterApply(log);
        
        notifyCenterMockedStatic.verify(
            () -> NotifyCenter.publishEvent(any(ConfigDumpEvent.class)));
    }
    
    @Test
    void testAfterApplyWithMultipleDumpEvents() {
        ConfigDumpEvent event = ConfigDumpEvent.builder()
            .dataId("d").group("g").namespaceId("ns")
            .content("content").build();
        String json = JacksonUtils.toJson(Collections.singletonList(event));
        
        Map<String, String> extendInfo = new HashMap<>();
        extendInfo.put(Constants.EXTEND_INFOS_CONFIG_DUMP_EVENT, json);
        
        WriteRequest log = WriteRequest.newBuilder()
            .putAllExtendInfo(extendInfo).build();
        hook.afterApply(log);
        
        notifyCenterMockedStatic.verify(
            () -> NotifyCenter.publishEvent(any(ConfigDumpEvent.class)));
    }
    
    @Test
    void testAfterApplyWithBlankSingleEvent() {
        Map<String, String> extendInfo = new HashMap<>();
        extendInfo.put(Constants.EXTEND_INFO_CONFIG_DUMP_EVENT, "");
        
        WriteRequest log = WriteRequest.newBuilder()
            .putAllExtendInfo(extendInfo).build();
        hook.afterApply(log);
        
        notifyCenterMockedStatic.verify(
            () -> NotifyCenter.publishEvent(any(ConfigDumpEvent.class)),
            never());
    }
    
    @Test
    void testAfterApplyWithBlankMultipleEvents() {
        Map<String, String> extendInfo = new HashMap<>();
        extendInfo.put(Constants.EXTEND_INFOS_CONFIG_DUMP_EVENT, "");
        
        WriteRequest log = WriteRequest.newBuilder()
            .putAllExtendInfo(extendInfo).build();
        hook.afterApply(log);
        
        notifyCenterMockedStatic.verify(
            () -> NotifyCenter.publishEvent(any(ConfigDumpEvent.class)),
            never());
    }
    
    @Test
    void testAfterApplyWithEmptyExtendInfo() {
        WriteRequest log = WriteRequest.newBuilder().build();
        hook.afterApply(log);
        
        notifyCenterMockedStatic.verify(
            () -> NotifyCenter.publishEvent(any(ConfigDumpEvent.class)),
            never());
    }
}
