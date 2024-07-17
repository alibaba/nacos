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
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * The type Default history config cleaner test.
 */
@ExtendWith(SpringExtension.class)
public class DefaultHistoryConfigCleanerTest {
    
    private DefaultHistoryConfigCleaner defaultHistoryConfigCleaner = new DefaultHistoryConfigCleaner();
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    @Mock
    private CPProtocol cpProtocol;
    
    /**
     * The Application utils mocked static.
     */
    MockedStatic<ApplicationUtils> applicationUtilsMockedStatic;
    
    MockedStatic<ConfigExecutor> configExecutorMocked;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    /**
     * Sets up.
     */
    @BeforeEach
    public void setUp() {
        applicationUtilsMockedStatic = Mockito.mockStatic(ApplicationUtils.class);
        applicationUtilsMockedStatic.when(() -> ApplicationUtils.getBean(ServerMemberManager.class))
                .thenReturn(memberManager);
        applicationUtilsMockedStatic.when(() -> ApplicationUtils.getBean(ProtocolManager.class))
                .thenReturn(protocolManager);
        applicationUtilsMockedStatic.when(() -> ApplicationUtils.getBean(HistoryConfigInfoPersistService.class))
                .thenReturn(historyConfigInfoPersistService);
        
        configExecutorMocked = Mockito.mockStatic(ConfigExecutor.class);
    }
    
    /**
     * End.
     */
    @AfterEach
    public void end() {
        applicationUtilsMockedStatic.close();
        configExecutorMocked.close();
    }
    
    /**
     * Test.
     */
    @Test
    public void test() {
        HistoryConfigCleaner configCleaner = HistoryConfigCleanerManager.getHistoryConfigCleaner("nacos");
        configCleaner.startCleanTask();
        assertEquals(configCleaner.getName(), "nacos");
    }
    
    /**
     * Test can execute.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCanExecute() throws Exception {
        Method method = DefaultHistoryConfigCleaner.class.getDeclaredMethod("canExecute");
        method.setAccessible(true);
        
        DatasourceConfiguration.setEmbeddedStorage(true);
        EnvUtil.setIsStandalone(true);
        assertTrue((boolean) method.invoke(defaultHistoryConfigCleaner));
        
        EnvUtil.setIsStandalone(false);
        Mockito.when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        Mockito.when(cpProtocol.isLeader(anyString())).thenReturn(true);
        assertTrue((boolean) method.invoke(defaultHistoryConfigCleaner));
        
        Mockito.when(cpProtocol.isLeader(anyString())).thenReturn(false);
        assertFalse((boolean) method.invoke(defaultHistoryConfigCleaner));
        
        DatasourceConfiguration.setEmbeddedStorage(false);
        Mockito.when(memberManager.isFirstIp()).thenReturn(true);
        assertTrue((boolean) method.invoke(defaultHistoryConfigCleaner));
    }
    
    /**
     * Test clean config history.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCleanConfigHistory() throws Exception {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.retention.days")).thenReturn("30");
        
        DatasourceConfiguration.setEmbeddedStorage(false);
        Mockito.when(memberManager.isFirstIp()).thenReturn(false);
        defaultHistoryConfigCleaner.cleanConfigHistory();
        Mockito.verify(historyConfigInfoPersistService, Mockito.times(0))
                .removeConfigHistory(any(Timestamp.class), anyInt());
        
        DatasourceConfiguration.setEmbeddedStorage(true);
        envUtilMockedStatic.when(EnvUtil::getStandaloneMode).thenReturn(true);
        EnvUtil.setIsStandalone(true);
        defaultHistoryConfigCleaner.cleanConfigHistory();
        Mockito.verify(historyConfigInfoPersistService, Mockito.times(1))
                .removeConfigHistory(any(Timestamp.class), anyInt());
        envUtilMockedStatic.close();
    }
    
    /**
     * Test start clean task.
     *
     * @throws Exception the exception
     */
    @Test
    public void testStartCleanTask() throws Exception {
        defaultHistoryConfigCleaner.startCleanTask();
        configExecutorMocked.verify(
                () -> ConfigExecutor.scheduleConfigTask(any(), anyLong(), anyLong(), eq(TimeUnit.MINUTES)),
                Mockito.times(1));
    }
    
}