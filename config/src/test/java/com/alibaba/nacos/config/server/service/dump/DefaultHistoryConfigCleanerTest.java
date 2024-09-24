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

import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

@ExtendWith(SpringExtension.class)
public class DefaultHistoryConfigCleanerTest {
    
    private DefaultHistoryConfigCleaner defaultHistoryConfigCleaner = new DefaultHistoryConfigCleaner();
    
    @Mock
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    MockedStatic<ApplicationUtils> applicationUtilsMockedStatic;
    
    MockedStatic<ConfigExecutor> configExecutorMocked;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    /**
     * Sets up.
     */
    @BeforeEach
    public void setUp() {
        applicationUtilsMockedStatic = Mockito.mockStatic(ApplicationUtils.class);
        applicationUtilsMockedStatic.when(() -> ApplicationUtils.getBean(HistoryConfigInfoPersistService.class))
                .thenReturn(historyConfigInfoPersistService);
        
        configExecutorMocked = Mockito.mockStatic(ConfigExecutor.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
    }
    
    /**
     * End.
     */
    @AfterEach
    public void end() {
        applicationUtilsMockedStatic.close();
        configExecutorMocked.close();
        envUtilMockedStatic.close();
    }
    
    @Test
    public void test() {
        HistoryConfigCleaner configCleaner = HistoryConfigCleanerManager.getHistoryConfigCleaner("nacos");
        assertEquals(configCleaner.getName(), "nacos");
    }
    
    @Test
    public void testCleanHistoryConfig() throws Exception {
        defaultHistoryConfigCleaner.cleanHistoryConfig();
        Mockito.verify(historyConfigInfoPersistService, Mockito.times(1))
                .removeConfigHistory(any(Timestamp.class), anyInt());
    }
    
    @Test
    public void testGetRetentionDays() throws Exception {
        Method method = DefaultHistoryConfigCleaner.class.getDeclaredMethod("getRetentionDays");
        method.setAccessible(true);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.retention.days")).thenReturn("-1");
        assertEquals((int) method.invoke(defaultHistoryConfigCleaner), 30);
        
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.retention.days")).thenReturn("30");
        assertEquals((int) method.invoke(defaultHistoryConfigCleaner), 30);
        
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.retention.days")).thenReturn("1");
        assertEquals((int) method.invoke(defaultHistoryConfigCleaner), 1);
    }
}