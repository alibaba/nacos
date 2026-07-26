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

package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.common.utils.Observable;
import com.alibaba.nacos.common.utils.Observer;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.consistency.ProtocolMetaData;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.MetadataKey;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddedDumpServiceTest {
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private NamespacePersistService namespacePersistService;
    
    @Mock
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    @Mock
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private CPProtocol protocol;
    
    @Mock
    private ProtocolMetaData protocolMetaData;
    
    @Mock
    private DataSourceService dataSourceService;
    
    private MockedStatic<EnvUtil> envUtilMockedStatic;
    
    private MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    
    private TestEmbeddedDumpService dumpService;
    
    @BeforeEach
    void setUp() {
        envUtilMockedStatic = mockStatic(EnvUtil.class);
        envUtilMockedStatic.when(() -> EnvUtil.getAvailableProcessors(anyInt()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        propertyUtilMockedStatic = mockStatic(PropertyUtil.class);
        propertyUtilMockedStatic.when(PropertyUtil::getAllDumpPageSize).thenReturn(100);
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "localDataSourceService",
            dataSourceService);
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "basicDataSourceService",
            dataSourceService);
        dumpService = new TestEmbeddedDumpService(configInfoPersistService,
            namespacePersistService, historyConfigInfoPersistService,
            configInfoGrayPersistService, memberManager, protocolManager);
    }
    
    @AfterEach
    void tearDown() {
        envUtilMockedStatic.close();
        propertyUtilMockedStatic.close();
    }
    
    @Test
    void testInitWhenStandalone() throws Throwable {
        envUtilMockedStatic.when(EnvUtil::getStandaloneMode).thenReturn(true);
        
        dumpService.init();
        
        assertEquals(1, dumpService.getDumpOperateCount());
    }
    
    @Test
    void testInitWhenClusterModeSubscribesLeaderMetadata() throws Throwable {
        prepareClusterMode();
        try (MockedStatic<ThreadUtils> threadUtilsMockedStatic =
            mockStatic(ThreadUtils.class)) {
            dumpService.init();
        }
        
        verify(protocolMetaData).subscribe(eq(PersistenceConstant.CONFIG_MODEL_RAFT_GROUP),
            eq(MetadataKey.LEADER_META_DATA), any(Observer.class));
    }
    
    @Test
    void testObserverIgnoresOtherObservable() throws Throwable {
        Observer observer = initAndCaptureObserver();
        
        observer.update(mock(Observable.class));
        
        assertEquals(0, dumpService.getDumpOperateCount());
    }
    
    @Test
    void testObserverIgnoresNullLeaderMetadata() throws Throwable {
        Observer observer = initAndCaptureObserver();
        try (MockedStatic<GlobalExecutor> globalExecutor = mockGlobalExecutor()) {
            observer.update(new ProtocolMetaData.ValueItem("test/path"));
            runGlobalTask(globalExecutor);
        }
        
        assertEquals(0, dumpService.getDumpOperateCount());
    }
    
    @Test
    void testObserverDumpsAndUnsubscribes() throws Throwable {
        Observer observer = initAndCaptureObserver();
        try (MockedStatic<GlobalExecutor> globalExecutor = mockGlobalExecutor();
            MockedStatic<ThreadUtils> threadUtilsMockedStatic =
                mockStatic(ThreadUtils.class)) {
            observer.update(valueItemWithData("leader"));
            runGlobalTask(globalExecutor);
        }
        
        assertEquals(1, dumpService.getDumpOperateCount());
        verify(protocolMetaData).unSubscribe(PersistenceConstant.CONFIG_MODEL_RAFT_GROUP,
            MetadataKey.LEADER_META_DATA, observer);
    }
    
    @Test
    void testObserverRetriesTemporaryFailure() throws Throwable {
        Observer observer = initAndCaptureObserver();
        dumpService.addFailure(new RuntimeException(
            "The conformance protocol is temporarily unavailable for reading"));
        try (MockedStatic<GlobalExecutor> globalExecutor = mockGlobalExecutor();
            MockedStatic<ThreadUtils> threadUtilsMockedStatic =
                mockStatic(ThreadUtils.class)) {
            observer.update(valueItemWithData("leader"));
            runGlobalTask(globalExecutor);
        }
        
        assertEquals(2, dumpService.getDumpOperateCount());
        verify(protocolMetaData).unSubscribe(PersistenceConstant.CONFIG_MODEL_RAFT_GROUP,
            MetadataKey.LEADER_META_DATA, observer);
    }
    
    @Test
    void testInitThrowsNonRetryFailure() {
        prepareClusterMode();
        dumpService.addFailure(new RuntimeException("STATE_ERROR"));
        doAnswer(invocation -> {
            Observer observer = invocation.getArgument(2, Observer.class);
            try (MockedStatic<GlobalExecutor> globalExecutor = mockGlobalExecutor()) {
                observer.update(valueItemWithData("leader"));
                runGlobalTask(globalExecutor);
            }
            return null;
        }).when(protocolMetaData)
            .subscribe(eq(PersistenceConstant.CONFIG_MODEL_RAFT_GROUP),
                eq(MetadataKey.LEADER_META_DATA), any(Observer.class));
        
        try (MockedStatic<ThreadUtils> threadUtilsMockedStatic =
            mockStatic(ThreadUtils.class)) {
            assertThrows(RuntimeException.class, () -> dumpService.init());
        }
    }
    
    @Test
    void testShouldRetry() throws Exception {
        assertTrue(invokeShouldRetry(new RuntimeException(
            "The conformance protocol is temporarily unavailable for reading")));
        assertFalse(invokeShouldRetry(new RuntimeException("FSMCaller is overload.")));
        assertFalse(invokeShouldRetry(new RuntimeException("STATE_ERROR")));
        assertFalse(invokeShouldRetry(new RuntimeException("other error")));
    }
    
    @Test
    void testCanExecuteWhenStandalone() {
        envUtilMockedStatic.when(EnvUtil::getStandaloneMode).thenReturn(true);
        
        assertTrue(dumpService.canExecute());
    }
    
    @Test
    void testCanExecuteWhenLeader() {
        envUtilMockedStatic.when(EnvUtil::getStandaloneMode).thenReturn(false);
        when(protocolManager.getCpProtocol()).thenReturn(protocol);
        when(protocol.isLeader(PersistenceConstant.CONFIG_MODEL_RAFT_GROUP)).thenReturn(true);
        
        assertTrue(dumpService.canExecute());
    }
    
    @Test
    void testCanExecuteWhenFollower() {
        envUtilMockedStatic.when(EnvUtil::getStandaloneMode).thenReturn(false);
        when(protocolManager.getCpProtocol()).thenReturn(protocol);
        when(protocol.isLeader(PersistenceConstant.CONFIG_MODEL_RAFT_GROUP)).thenReturn(false);
        
        assertFalse(dumpService.canExecute());
    }
    
    private Observer initAndCaptureObserver() throws Throwable {
        prepareClusterMode();
        ArgumentCaptor<Observer> captor = ArgumentCaptor.forClass(Observer.class);
        try (MockedStatic<ThreadUtils> threadUtilsMockedStatic =
            mockStatic(ThreadUtils.class)) {
            dumpService.init();
        }
        verify(protocolMetaData).subscribe(eq(PersistenceConstant.CONFIG_MODEL_RAFT_GROUP),
            eq(MetadataKey.LEADER_META_DATA), captor.capture());
        return captor.getValue();
    }
    
    private void prepareClusterMode() {
        envUtilMockedStatic.when(EnvUtil::getStandaloneMode).thenReturn(false);
        when(protocolManager.getCpProtocol()).thenReturn(protocol);
        when(protocol.protocolMetaData()).thenReturn(protocolMetaData);
    }
    
    private ProtocolMetaData.ValueItem valueItemWithData(Object data) {
        ProtocolMetaData.ValueItem valueItem = new ProtocolMetaData.ValueItem("test/path");
        ReflectionTestUtils.setField(valueItem, "data", data);
        return valueItem;
    }
    
    private MockedStatic<GlobalExecutor> mockGlobalExecutor() {
        return mockStatic(GlobalExecutor.class);
    }
    
    private void runGlobalTask(MockedStatic<GlobalExecutor> globalExecutor) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        globalExecutor.verify(() -> GlobalExecutor.executeByCommon(captor.capture()));
        captor.getValue().run();
    }
    
    private boolean invokeShouldRetry(Throwable throwable) throws Exception {
        Method method = EmbeddedDumpService.class.getDeclaredMethod("shouldRetry",
            Throwable.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(dumpService, throwable);
    }
    
    private static class TestEmbeddedDumpService extends EmbeddedDumpService {
        
        private final Queue<Throwable> failures = new ArrayDeque<>();
        
        private int dumpOperateCount;
        
        TestEmbeddedDumpService(ConfigInfoPersistService configInfoPersistService,
            NamespacePersistService namespacePersistService,
            HistoryConfigInfoPersistService historyConfigInfoPersistService,
            ConfigInfoGrayPersistService configInfoGrayPersistService,
            ServerMemberManager memberManager, ProtocolManager protocolManager) {
            super(configInfoPersistService, namespacePersistService,
                historyConfigInfoPersistService,
                configInfoGrayPersistService, memberManager, protocolManager);
        }
        
        @Override
        protected void dumpOperate() {
            dumpOperateCount++;
            Throwable failure = failures.poll();
            if (failure != null) {
                throw new RuntimeException(failure);
            }
        }
        
        void addFailure(Throwable failure) {
            failures.add(failure);
        }
        
        int getDumpOperateCount() {
            return dumpOperateCount;
        }
    }
}
