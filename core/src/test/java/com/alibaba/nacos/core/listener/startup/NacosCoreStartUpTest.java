/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.listener.startup;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import com.alibaba.nacos.sys.file.FileWatcher;
import com.alibaba.nacos.sys.file.WatchFileCenter;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * {@link NacosCoreStartUp} unit test.
 */
@ExtendWith(MockitoExtension.class)
class NacosCoreStartUpTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosCoreStartUpTest.class);
    
    @AfterEach
    void tearDown() {
        System.clearProperty("nacos.mode");
        System.clearProperty("nacos.function.mode");
        System.clearProperty("nacos.local.ip");
    }
    
    @Test
    void startUpPhaseReturnsCore() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        assertEquals(NacosStartUp.CORE_START_UP_PHASE, startUp.startUpPhase());
    }
    
    @Test
    void makeWorkDirCreatesLogsConfData() throws Exception {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        String nacosHome = java.nio.file.Files.createTempDirectory("nacos_test").toString();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class);
            MockedStatic<DiskUtils> diskMock = mockStatic(DiskUtils.class)) {
            envMock.when(EnvUtil::getNacosHome).thenReturn(nacosHome);
            String[] dirs = startUp.makeWorkDir();
            assertNotNull(dirs);
            assertEquals(3, dirs.length);
            diskMock.verify(() -> DiskUtils.forceMkdir(any(File.class)),
                org.mockito.Mockito.times(3));
        }
    }
    
    @Test
    void injectEnvironmentSetsEnvUtil() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        ConfigurableEnvironment environment = new MockEnvironment();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class)) {
            startUp.injectEnvironment(environment);
            envMock.verify(() -> EnvUtil.setEnvironment(environment));
        }
    }
    
    @Test
    void initSystemPropertyStandaloneMode() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class);
            MockedStatic<InetUtils> inetMock = mockStatic(InetUtils.class)) {
            envMock.when(EnvUtil::getStandaloneMode).thenReturn(true);
            envMock.when(EnvUtil::getFunctionMode).thenReturn(null);
            inetMock.when(InetUtils::getSelfIP).thenReturn("127.0.0.1");
            startUp.initSystemProperty();
            assertEquals("stand alone", System.getProperty("nacos.mode"));
            assertEquals("All", System.getProperty("nacos.function.mode"));
            assertEquals("127.0.0.1", System.getProperty("nacos.local.ip"));
        }
    }
    
    @Test
    void initSystemPropertyClusterMode() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class);
            MockedStatic<InetUtils> inetMock = mockStatic(InetUtils.class)) {
            envMock.when(EnvUtil::getStandaloneMode).thenReturn(false);
            envMock.when(EnvUtil::getFunctionMode).thenReturn(null);
            inetMock.when(InetUtils::getSelfIP).thenReturn("192.168.1.1");
            startUp.initSystemProperty();
            assertEquals("cluster", System.getProperty("nacos.mode"));
        }
    }
    
    @Test
    void initSystemPropertyFunctionModeConfig() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class);
            MockedStatic<InetUtils> inetMock = mockStatic(InetUtils.class)) {
            envMock.when(EnvUtil::getStandaloneMode).thenReturn(true);
            envMock.when(EnvUtil::getFunctionMode).thenReturn(EnvUtil.FUNCTION_MODE_CONFIG);
            inetMock.when(InetUtils::getSelfIP).thenReturn("127.0.0.1");
            startUp.initSystemProperty();
            assertEquals(EnvUtil.FUNCTION_MODE_CONFIG, System.getProperty("nacos.function.mode"));
        }
    }
    
    @Test
    void initSystemPropertyFunctionModeNaming() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class);
            MockedStatic<InetUtils> inetMock = mockStatic(InetUtils.class)) {
            envMock.when(EnvUtil::getStandaloneMode).thenReturn(true);
            envMock.when(EnvUtil::getFunctionMode).thenReturn(EnvUtil.FUNCTION_MODE_NAMING);
            inetMock.when(InetUtils::getSelfIP).thenReturn("127.0.0.1");
            startUp.initSystemProperty();
            assertEquals(EnvUtil.FUNCTION_MODE_NAMING, System.getProperty("nacos.function.mode"));
        }
    }
    
    @Test
    void initSystemPropertyFunctionModeMicroService() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class);
            MockedStatic<InetUtils> inetMock = mockStatic(InetUtils.class)) {
            envMock.when(EnvUtil::getStandaloneMode).thenReturn(true);
            envMock.when(EnvUtil::getFunctionMode).thenReturn(EnvUtil.FUNCTION_MODE_MICROSERVICE);
            inetMock.when(InetUtils::getSelfIP).thenReturn("127.0.0.1");
            startUp.initSystemProperty();
            assertEquals(EnvUtil.FUNCTION_MODE_MICROSERVICE,
                System.getProperty("nacos.function.mode"));
        }
    }
    
    @Test
    void initSystemPropertyFunctionModeAi() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class);
            MockedStatic<InetUtils> inetMock = mockStatic(InetUtils.class)) {
            envMock.when(EnvUtil::getStandaloneMode).thenReturn(true);
            envMock.when(EnvUtil::getFunctionMode).thenReturn(EnvUtil.FUNCTION_MODE_AI);
            inetMock.when(InetUtils::getSelfIP).thenReturn("127.0.0.1");
            startUp.initSystemProperty();
            assertEquals(EnvUtil.FUNCTION_MODE_AI, System.getProperty("nacos.function.mode"));
        }
    }
    
    @Test
    void customEnvironmentCallsEnvUtil() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class)) {
            startUp.customEnvironment();
            envMock.verify(EnvUtil::customEnvironment);
        }
    }
    
    @Test
    void startedSetsApplicationUtilsStarted() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        startUp.starting();
        try (MockedStatic<ApplicationUtils> appMock = mockStatic(ApplicationUtils.class)) {
            startUp.started();
            appMock.verify(() -> ApplicationUtils.setStarted(true));
        }
    }
    
    @Test
    void logStartedLogsWithStorageMode() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        startUp.starting();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class)) {
            envMock.when(EnvUtil::getEnvironment).thenReturn(new MockEnvironment());
            startUp.logStarted(LOGGER);
        }
        startUp.started();
    }
    
    @Test
    void failedShutsDownServices() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        startUp.starting();
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        try (MockedStatic<com.alibaba.nacos.common.executor.ThreadPoolManager> tpMock =
            mockStatic(com.alibaba.nacos.common.executor.ThreadPoolManager.class);
            MockedStatic<com.alibaba.nacos.sys.file.WatchFileCenter> wfMock =
                mockStatic(com.alibaba.nacos.sys.file.WatchFileCenter.class);
            MockedStatic<com.alibaba.nacos.common.notify.NotifyCenter> ncMock =
                mockStatic(com.alibaba.nacos.common.notify.NotifyCenter.class)) {
            startUp.failed(new RuntimeException("test"), context);
            verify(context).close();
            tpMock.verify(com.alibaba.nacos.common.executor.ThreadPoolManager::shutdown);
            wfMock.verify(com.alibaba.nacos.sys.file.WatchFileCenter::shutdown);
            ncMock.verify(com.alibaba.nacos.common.notify.NotifyCenter::shutdown);
        }
    }
    
    @Test
    void loadPrePropertiesThrowsWhenLoadFails() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        ConfigurableEnvironment environment = new MockEnvironment();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class)) {
            envMock.when(EnvUtil::getApplicationConfFileResource)
                .thenThrow(new RuntimeException("no resource"));
            assertThrows(NacosRuntimeException.class, () -> startUp.loadPreProperties(environment));
        }
    }
    
    @Test
    void makeWorkDirThrowsWhenForceMkdirFails() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        String nacosHome = "/tmp/nacos-test";
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class);
            MockedStatic<DiskUtils> diskMock = mockStatic(DiskUtils.class)) {
            envMock.when(EnvUtil::getNacosHome).thenReturn(nacosHome);
            diskMock.when(() -> DiskUtils.forceMkdir(any(File.class)))
                .thenThrow(new IOException("perm denied"));
            assertThrows(NacosRuntimeException.class, startUp::makeWorkDir);
        }
    }
    
    @Test
    void logStartingInfoInClusterModeLogsClusterConf() throws Exception {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class)) {
            envMock.when(EnvUtil::getStandaloneMode).thenReturn(false);
            envMock.when(EnvUtil::readClusterConf)
                .thenReturn(java.util.Arrays.asList("127.0.0.1:8848"));
            startUp.starting();
            startUp.logStartingInfo(LOGGER);
            startUp.started();
        }
    }
    
    @Test
    void logStartingInfoInClusterModeWhenReadClusterConfThrows() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class)) {
            envMock.when(EnvUtil::getStandaloneMode).thenReturn(false);
            envMock.when(EnvUtil::readClusterConf).thenThrow(new IOException("read fail"));
            startUp.starting();
            startUp.logStartingInfo(LOGGER);
            startUp.started();
        }
    }
    
    @Test
    void logStartedWithExternalStorageMode() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        startUp.starting();
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.sql.init.platform", "mysql");
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class)) {
            envMock.when(EnvUtil::getEnvironment).thenReturn(env);
            System.setProperty("nacos.mode", "stand alone");
            startUp.logStarted(LOGGER);
        } finally {
            System.clearProperty("nacos.mode");
        }
        startUp.started();
    }
    
    @Test
    void logStartedWithEmbeddedStorageMode() {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        startUp.starting();
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class)) {
            envMock.when(EnvUtil::getEnvironment).thenReturn(new MockEnvironment());
            envMock.when(EnvUtil::getStandaloneMode).thenReturn(true);
            System.setProperty("nacos.mode", "stand alone");
            startUp.logStarted(LOGGER);
        } finally {
            System.clearProperty("nacos.mode");
        }
        startUp.started();
    }
    
    @Test
    void loadPrePropertiesSuccessRegistersWatcherAndWatcherInterestAndOnChange() throws Exception {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        ConfigurableEnvironment environment = new MockEnvironment();
        Resource resource = mock(Resource.class);
        Map<String, ?> props = Collections.singletonMap("key", "value");
        ArgumentCaptor<FileWatcher> watcherCaptor = ArgumentCaptor.forClass(FileWatcher.class);
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class);
            MockedStatic<WatchFileCenter> wfMock = mockStatic(WatchFileCenter.class)) {
            envMock.when(EnvUtil::getApplicationConfFileResource).thenReturn(resource);
            envMock.when(() -> EnvUtil.loadProperties(resource)).thenReturn(props);
            envMock.when(EnvUtil::getConfPath).thenReturn("/conf");
            startUp.loadPreProperties(environment);
            wfMock.verify(() -> WatchFileCenter.registerWatcher(
                org.mockito.ArgumentMatchers.eq("/conf"), watcherCaptor.capture()));
            FileWatcher watcher = watcherCaptor.getValue();
            assertTrue(watcher.interest("path/application.properties"));
            assertFalse(watcher.interest("other"));
            watcher.onChange(null);
        }
    }
    
    @Test
    void loadPrePropertiesRegisteredWatcherOnChangeIgnoresIoException() throws Exception {
        NacosCoreStartUp startUp = new NacosCoreStartUp();
        ConfigurableEnvironment environment = new MockEnvironment();
        Resource resource = mock(Resource.class);
        Map<String, ?> props = Collections.singletonMap("k", "v");
        ArgumentCaptor<FileWatcher> watcherCaptor = ArgumentCaptor.forClass(FileWatcher.class);
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class);
            MockedStatic<WatchFileCenter> wfMock = mockStatic(WatchFileCenter.class)) {
            envMock.when(EnvUtil::getApplicationConfFileResource).thenReturn(resource);
            envMock.when(() -> EnvUtil.loadProperties(resource)).thenReturn(props)
                .thenThrow(new IOException("load fail"));
            envMock.when(EnvUtil::getConfPath).thenReturn("/conf");
            startUp.loadPreProperties(environment);
            wfMock.verify(() -> WatchFileCenter.registerWatcher(
                org.mockito.ArgumentMatchers.eq("/conf"), watcherCaptor.capture()));
            FileWatcher watcher = watcherCaptor.getValue();
            watcher.onChange(null);
        }
    }
}
