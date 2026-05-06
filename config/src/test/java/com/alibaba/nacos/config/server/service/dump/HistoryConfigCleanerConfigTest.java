/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(SpringExtension.class)
class HistoryConfigCleanerConfigTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    @BeforeEach
    public void before() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
    }
    
    @Test
    public void test() {
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(anyString(), any(), anyString())).thenReturn("test");
        HistoryConfigCleanerConfig historyConfigCleanerConfig = HistoryConfigCleanerConfig.getInstance();
        historyConfigCleanerConfig.getConfigFromEnv();
        assertEquals("test", historyConfigCleanerConfig.getActiveHistoryConfigCleaner());
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(anyString(), any(), anyString())).thenReturn(null);
        historyConfigCleanerConfig.getConfigFromEnv();
        assertEquals("nacos", historyConfigCleanerConfig.getActiveHistoryConfigCleaner());
    }
    
    @AfterEach
    public void after() {
        envUtilMockedStatic.close();
    }
    
}