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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
class HistoryConfigCleanerConfigTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    @Test
    public void test() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.history.clear.name", String.class, "nacos"))
                .thenReturn("test");
        HistoryConfigCleanerConfig historyConfigCleanerConfig = HistoryConfigCleanerConfig.getInstance();
        assertEquals("test", historyConfigCleanerConfig.getActiveHistoryConfigCleaner());
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.history.clear.name", String.class, "nacos"))
                .thenReturn(null);
        historyConfigCleanerConfig.getConfigFromEnv();
        assertEquals("nacos", historyConfigCleanerConfig.getActiveHistoryConfigCleaner());
        envUtilMockedStatic.close();
    }
    
}