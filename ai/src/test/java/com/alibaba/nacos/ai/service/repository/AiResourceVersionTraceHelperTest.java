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

package com.alibaba.nacos.ai.service.repository;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceVersionTraceHelperTest {
    
    @AfterEach
    void cleanUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
    }
    
    @Test
    void buildExtShouldContainSqlOperationRowsAndError() {
        String ext = AiResourceVersionTraceHelper.buildExt("UPDATE_STATUS", 3, "timeout");
        @SuppressWarnings("unchecked")
        Map<String, Object> extMap = JacksonUtils.toObj(ext, Map.class);
        
        assertEquals("UPDATE_STATUS", extMap.get("sqlOperation"));
        assertEquals(3, ((Number) extMap.get("rowsAffected")).intValue());
        assertEquals("timeout", extMap.get("error"));
    }
    
    @Test
    void buildExtShouldUseDefaultSqlOperation() {
        String ext = AiResourceVersionTraceHelper.buildExt("", null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> extMap = JacksonUtils.toObj(ext, Map.class);
        
        assertEquals("-", extMap.get("sqlOperation"));
    }
    
    @Test
    void shouldReturnFalseByDefaultForSuccessTraceSwitch() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        assertFalse(AiResourceVersionTraceHelper.isPersistSuccessTraceEnabled());
    }
    
    @Test
    void shouldReadSuccessTraceSwitchFromEnvironment() {
        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources propertySources = env.getPropertySources();
        propertySources.addFirst(new MapPropertySource("test-ai-trace", Map.of(
                AiResourceVersionTraceHelper.TRACE_PERSIST_SUCCESS_ENABLED_KEY, "true"
        )));
        EnvUtil.setEnvironment(env);
        
        assertTrue(AiResourceVersionTraceHelper.isPersistSuccessTraceEnabled());
    }
}
