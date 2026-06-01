/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.alibaba.nacos.core.persistence;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.exception.ConsistencyException;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.datasource.LocalDataSourceServiceImpl;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.persistence.model.event.RaftDbErrorEvent;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.sql.ModifyRequest;
import com.alibaba.nacos.persistence.repository.embedded.sql.QueryType;
import com.alibaba.nacos.persistence.repository.embedded.sql.SelectRequest;
import com.alibaba.nacos.persistence.utils.PersistenceExecutor;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.alibaba.nacos.sys.env.EnvUtil;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributedDatabaseOperateImplTest {
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private CPProtocol protocol;
    
    @Mock
    private LocalDataSourceServiceImpl dataSourceService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private TransactionTemplate transactionTemplate;
    
    @Mock
    private Member member;
    
    private MockedStatic<DynamicDataSource> dynamicDataSourceMock;
    private MockedStatic<NotifyCenter> notifyCenterMock;
    private MockedStatic<EmbeddedStorageContextHolder> embeddedStorageContextHolderMock;
    private MockedStatic<PersistenceExecutor> persistenceExecutorMock;
    
    private DistributedDatabaseOperateImpl impl;
    
    @BeforeEach
    void setUp() throws Exception {
        EnvUtil.setEnvironment(new MockEnvironment());
        dynamicDataSourceMock = Mockito.mockStatic(DynamicDataSource.class);
        notifyCenterMock = Mockito.mockStatic(NotifyCenter.class);
        embeddedStorageContextHolderMock = Mockito.mockStatic(EmbeddedStorageContextHolder.class);
        persistenceExecutorMock = Mockito.mockStatic(PersistenceExecutor.class);
        
        DynamicDataSource dataSourceHolder = Mockito.mock(DynamicDataSource.class);
        when(dataSourceHolder.getDataSource()).thenReturn(dataSourceService);
        dynamicDataSourceMock.when(DynamicDataSource::getInstance).thenReturn(dataSourceHolder);
        
        doNothing().when(dataSourceService).cleanAndReopenDerby();
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(dataSourceService.getTransactionTemplate()).thenReturn(transactionTemplate);
        
        notifyCenterMock.when(() -> NotifyCenter.registerToSharePublisher(any()))
            .then(invocation -> null);
        notifyCenterMock.when(() -> NotifyCenter.registerSubscriber(any()))
            .then(invocation -> null);
        notifyCenterMock.when(() -> NotifyCenter.publishEvent(any())).then(invocation -> null);
        
        embeddedStorageContextHolderMock
            .when(() -> EmbeddedStorageContextHolder.containsExtendInfo(any()))
            .thenReturn(false);
        embeddedStorageContextHolderMock.when(EmbeddedStorageContextHolder::getCurrentExtendInfo)
            .thenReturn(new HashMap<>());
        
        persistenceExecutorMock.when(() -> PersistenceExecutor.executeEmbeddedDump(any()))
            .thenAnswer(inv -> {
                inv.getArgument(0, Runnable.class).run();
                return null;
            });
        
        when(protocolManager.getCpProtocol()).thenReturn(protocol);
        lenient().doNothing().when(protocol).addRequestProcessors(anyList());
        
        lenient().when(memberManager.getSelf()).thenReturn(member);
        lenient().when(member.getAddress()).thenReturn("127.0.0.1:8848");
        
        impl = new DistributedDatabaseOperateImpl(memberManager, protocolManager);
    }
    
    @AfterEach
    void tearDown() {
        if (dynamicDataSourceMock != null) {
            dynamicDataSourceMock.close();
        }
        if (notifyCenterMock != null) {
            notifyCenterMock.close();
        }
        if (embeddedStorageContextHolderMock != null) {
            embeddedStorageContextHolderMock.close();
        }
        if (persistenceExecutorMock != null) {
            persistenceExecutorMock.close();
        }
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testGroup() {
        assertEquals("nacos_config", impl.group());
    }
    
    @Test
    void testMockConsistencyProtocol() {
        CPProtocol anotherProtocol = Mockito.mock(CPProtocol.class);
        impl.mockConsistencyProtocol(anotherProtocol);
        // no exception, verify later usage would use anotherProtocol
    }
    
    @Test
    void testOnError() {
        Throwable t = new RuntimeException("raft error");
        impl.onError(t);
        // publishes RaftDbErrorEvent - no exception
        notifyCenterMock.verify(() -> NotifyCenter.publishEvent(any(RaftDbErrorEvent.class)));
    }
    
    @Test
    void testLoadSnapshotOperate() {
        assertNotNull(impl.loadSnapshotOperate());
        assertEquals(1, impl.loadSnapshotOperate().size());
        assertTrue(impl.loadSnapshotOperate().get(0) instanceof DerbySnapshotOperation);
    }
    
    @Test
    void testUpdateSuccessWithoutConsumer() throws Exception {
        Response success = Response.newBuilder().setSuccess(true).build();
        when(protocol.write(any(WriteRequest.class))).thenReturn(success);
        
        Boolean result =
            impl.update(Collections.singletonList(new ModifyRequest("SELECT 1")), null);
        assertTrue(result);
    }
    
    @Test
    void testUpdateFailureWithoutConsumer() throws Exception {
        Response failure =
            Response.newBuilder().setSuccess(false).setErrMsg("write failed").build();
        when(protocol.write(any(WriteRequest.class))).thenReturn(failure);
        
        Boolean result =
            impl.update(Collections.singletonList(new ModifyRequest("SELECT 1")), null);
        assertFalse(result);
    }
    
    @Test
    void testUpdateWithConsumer() {
        Response success = Response.newBuilder().setSuccess(true).build();
        when(protocol.writeAsync(any(WriteRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(success));
        
        final boolean[] consumed = {false};
        Boolean result = impl.update(
            Collections.singletonList(new ModifyRequest("SELECT 1")),
            (ok, ex) -> consumed[0] = true);
        assertTrue(result);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(consumed[0]);
    }
    
    // ---------- query paths (mock protocol.getData / aGetData) ----------
    @Test
    void testQueryOneWithClassSuccess() throws Exception {
        Integer value = 42;
        byte[] serialized = SerializeFactory.getDefault().serialize(value);
        Response response =
            Response.newBuilder().setSuccess(true).setData(ByteString.copyFrom(serialized)).build();
        when(protocol.getData(any(ReadRequest.class))).thenReturn(response);
        
        Integer result = impl.queryOne("SELECT 1", Integer.class);
        assertEquals(42, result);
    }
    
    @Test
    void testQueryOneResponseFailure() throws Exception {
        Response response = Response.newBuilder().setSuccess(false).setErrMsg("db error").build();
        when(protocol.getData(any(ReadRequest.class))).thenReturn(response);
        
        assertThrows(NacosRuntimeException.class, () -> impl.queryOne("SELECT 1", Integer.class));
    }
    
    @Test
    void testQueryOneExceptionThrowsNacosRuntimeException() throws Exception {
        when(protocol.getData(any(ReadRequest.class))).thenThrow(new RuntimeException("net error"));
        
        assertThrows(NacosRuntimeException.class, () -> impl.queryOne("SELECT 1", Integer.class));
    }
    
    @Test
    void testQueryOneBlockRead() throws Exception {
        embeddedStorageContextHolderMock
            .when(() -> EmbeddedStorageContextHolder.containsExtendInfo(any()))
            .thenReturn(true);
        Integer value = 100;
        byte[] serialized = SerializeFactory.getDefault().serialize(value);
        Response response =
            Response.newBuilder().setSuccess(true).setData(ByteString.copyFrom(serialized)).build();
        when(protocol.aGetData(any(ReadRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(response));
        
        Integer result = impl.queryOne("SELECT id FROM t", Integer.class);
        assertEquals(100, result);
    }
    
    @Test
    void testQueryOneWithArgsAndClass() throws Exception {
        Long value = 999L;
        byte[] serialized = SerializeFactory.getDefault().serialize(value);
        when(protocol.getData(any(ReadRequest.class)))
            .thenReturn(Response.newBuilder().setSuccess(true)
                .setData(ByteString.copyFrom(serialized)).build());
        
        Long result = impl.queryOne("SELECT 1", new Object[] {"a"}, Long.class);
        assertEquals(999L, result);
    }
    
    @Test
    void testQueryManyWithList() throws Exception {
        List<Map<String, Object>> list =
            Collections.singletonList(Collections.singletonMap("k", "v"));
        byte[] serialized = SerializeFactory.getDefault().serialize(list);
        when(protocol.getData(any(ReadRequest.class)))
            .thenReturn(Response.newBuilder().setSuccess(true)
                .setData(ByteString.copyFrom(serialized)).build());
        
        List<Map<String, Object>> result = impl.queryMany("SELECT * FROM t", new Object[] {});
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    @Test
    void testQueryManyResponseFailure() throws Exception {
        when(protocol.getData(any(ReadRequest.class)))
            .thenReturn(Response.newBuilder().setSuccess(false).setErrMsg("err").build());
        
        assertThrows(NacosRuntimeException.class,
            () -> impl.queryMany("SELECT * FROM t", new Object[] {}, Integer.class));
    }
    
    // ---------- update exception paths ----------
    @Test
    void testUpdateThrowsTimeoutException() throws Exception {
        when(protocol.write(any(WriteRequest.class))).thenThrow(new TimeoutException("timeout"));
        
        assertThrows(NacosRuntimeException.class,
            () -> impl.update(Collections.singletonList(new ModifyRequest("UPDATE t SET x=1")),
                null));
    }
    
    @Test
    void testUpdateThrowsThrowable() throws Exception {
        when(protocol.write(any(WriteRequest.class))).thenThrow(new RuntimeException("io error"));
        
        assertThrows(NacosRuntimeException.class,
            () -> impl.update(Collections.singletonList(new ModifyRequest("UPDATE t SET x=1")),
                null));
    }
    
    @Test
    void testUpdateWithConsumerAsyncFailureStillReturnsTrue() throws Exception {
        when(protocol.writeAsync(any(WriteRequest.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("async failed")));
        
        Boolean result = impl.update(
            Collections.singletonList(new ModifyRequest("INSERT INTO t VALUES (1)")),
            (ok, e) -> {
            });
        assertTrue(result);
    }
    
    // ---------- dataImport ----------
    @Test
    void testDataImportSuccess() throws Exception {
        File file = Files.createTempFile("nacos-import-", ".sql").toFile();
        try {
            Files.write(file.toPath(), "INSERT INTO t VALUES (1);".getBytes());
            Response success = Response.newBuilder().setSuccess(true).build();
            when(protocol.writeAsync(any(WriteRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(success));
            
            CompletableFuture<RestResult<String>> future = impl.dataImport(file);
            RestResult<String> restResult = future.get();
            assertTrue(restResult.ok());
        } finally {
            file.delete();
        }
    }
    
    @Test
    void testDataImportFailure() throws Exception {
        File file = Files.createTempFile("nacos-import-", ".sql").toFile();
        try {
            Files.write(file.toPath(), "INSERT INTO t VALUES (1);".getBytes());
            Response failed =
                Response.newBuilder().setSuccess(false).setErrMsg("write failed").build();
            when(protocol.writeAsync(any(WriteRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(failed));
            
            RestResult<String> restResult = impl.dataImport(file).get();
            assertFalse(restResult.ok());
            assertTrue(restResult.getMessage() != null
                && restResult.getMessage().contains("write failed"));
        } finally {
            file.delete();
        }
    }
    
    @Test
    void testDataImportException() throws Exception {
        File file = File.createTempFile("nacos-import-", ".sql");
        file.delete();
        CompletableFuture<RestResult<String>> future = impl.dataImport(file);
        RestResult<String> restResult = future.get();
        assertFalse(restResult.ok());
    }
    
    // ---------- onRequest ----------
    @Test
    void testOnRequestQueryOneNoMapperNoArgs() throws Exception {
        SelectRequest selectRequest = SelectRequest.builder()
            .queryType(QueryType.QUERY_ONE_NO_MAPPER_NO_ARGS)
            .sql("SELECT 1")
            .className(Integer.class.getCanonicalName())
            .build();
        byte[] requestData = SerializeFactory.getDefault().serialize(selectRequest);
        ReadRequest readRequest = ReadRequest.newBuilder().setGroup("nacos_config")
            .setData(ByteString.copyFrom(requestData)).build();
        
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(42);
        byte[] resultBytes = SerializeFactory.getDefault().serialize(42);
        Response response = impl.onRequest(readRequest);
        
        assertTrue(response.getSuccess());
        assertEquals(42, SerializeFactory.getDefault().deserialize(response.getData().toByteArray(),
            Integer.class));
    }
    
    @Test
    void testOnRequestExceptionReturnsFailureResponse() throws Exception {
        SelectRequest selectRequest = SelectRequest.builder()
            .queryType(QueryType.QUERY_ONE_NO_MAPPER_NO_ARGS)
            .sql("SELECT 1")
            .className(Integer.class.getCanonicalName())
            .build();
        ReadRequest readRequest = ReadRequest.newBuilder().setGroup("nacos_config")
            .setData(ByteString.copyFrom(SerializeFactory.getDefault().serialize(selectRequest)))
            .build();
        
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
            .thenThrow(new RuntimeException("db error"));
        
        Response response = impl.onRequest(readRequest);
        assertFalse(response.getSuccess());
        assertNotNull(response.getErrMsg());
    }
    
    @Test
    void testOnRequestUnsupportedQueryType() throws Exception {
        SelectRequest selectRequest = SelectRequest.builder()
            .queryType((byte) 99)
            .sql("SELECT 1")
            .className(Integer.class.getCanonicalName())
            .build();
        ReadRequest readRequest = ReadRequest.newBuilder().setGroup("nacos_config")
            .setData(ByteString.copyFrom(SerializeFactory.getDefault().serialize(selectRequest)))
            .build();
        
        Response response = impl.onRequest(readRequest);
        assertFalse(response.getSuccess());
    }
    
    // ---------- onApply ----------
    @Test
    void testOnApplySuccess() throws Exception {
        List<ModifyRequest> sqlContext =
            Collections.singletonList(new ModifyRequest("UPDATE t SET x=1"));
        byte[] data = SerializeFactory.getDefault().serialize(sqlContext);
        WriteRequest log = WriteRequest.newBuilder().setGroup("nacos_config")
            .setData(ByteString.copyFrom(data))
            .build();
        
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Boolean> callback = inv.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(null);
        });
        lenient().when(jdbcTemplate.update(any(String.class), Mockito.<Object>any())).thenReturn(1);
        
        Response response = impl.onApply(log);
        assertTrue(response.getSuccess());
    }
    
    @Test
    void testOnApplyBadSqlGrammarException() throws Exception {
        List<ModifyRequest> sqlContext =
            Collections.singletonList(new ModifyRequest("UPDATE t SET x=1"));
        WriteRequest log = WriteRequest.newBuilder().setGroup("nacos_config")
            .setData(ByteString.copyFrom(SerializeFactory.getDefault().serialize(sqlContext)))
            .build();
        
        when(transactionTemplate.execute(any()))
            .thenThrow(new BadSqlGrammarException("", "sql", new java.sql.SQLException()));
        
        Response response = impl.onApply(log);
        assertFalse(response.getSuccess());
    }
    
    @Test
    void testOnApplyDataAccessExceptionThrowsConsistencyException() throws Exception {
        List<ModifyRequest> sqlContext =
            Collections.singletonList(new ModifyRequest("UPDATE t SET x=1"));
        WriteRequest log = WriteRequest.newBuilder().setGroup("nacos_config")
            .setData(ByteString.copyFrom(SerializeFactory.getDefault().serialize(sqlContext)))
            .build();
        
        when(transactionTemplate.execute(any())).thenThrow(new DataAccessException("data access") {
        });
        
        assertThrows(ConsistencyException.class, () -> impl.onApply(log));
    }
    
    @Test
    void testOnApplyWithDataImportKey() throws Exception {
        List<ModifyRequest> sqlContext =
            Collections.singletonList(new ModifyRequest("INSERT INTO t VALUES (1)"));
        WriteRequest log = WriteRequest.newBuilder().setGroup("nacos_config")
            .setData(ByteString.copyFrom(SerializeFactory.getDefault().serialize(sqlContext)))
            .putExtendInfo("00--0-data_import-0--00", "true")
            .build();
        
        when(jdbcTemplate.batchUpdate(any(String[].class))).thenReturn(new int[] {1});
        
        Response response = impl.onApply(log);
        assertTrue(response.getSuccess());
    }
}
