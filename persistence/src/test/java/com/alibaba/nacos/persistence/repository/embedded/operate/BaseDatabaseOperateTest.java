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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.persistence.repository.embedded.operate;

import com.alibaba.nacos.persistence.exception.NJdbcException;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.sql.ModifyRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaseDatabaseOperateTest {
    
    private static final String TEST_SQL = "UPDATE config_info SET data_id = 'test' WHERE id = ?;";
    
    private static final Object[] ARGS = new Object[1];
    
    @Spy
    BaseDatabaseOperate baseDatabaseOperate;
    
    @Mock
    BiConsumer<Boolean, Throwable> consumer;
    
    @Mock
    TransactionTemplate transactionTemplate;
    
    @Mock
    JdbcTemplate jdbcTemplate;
    
    @AfterEach
    void tearDown() {
        EmbeddedStorageContextHolder.cleanAllContext();
    }
    
    @Test
    void testUpdateSuccessWithConsumer() {
        List<ModifyRequest> requests = mockRequest(false);
        when(transactionTemplate.execute(any(TransactionCallback.class))).then(invocationOnMock -> {
            TransactionCallback callback = invocationOnMock.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        when(jdbcTemplate.update(TEST_SQL, ARGS)).thenReturn(1);
        assertTrue(baseDatabaseOperate.update(transactionTemplate, jdbcTemplate, requests, consumer));
        verify(consumer).accept(eq(Boolean.TRUE), eq(null));
    }
    
    @Test
    void testUpdateSuccessWithConsumerAndRollback() {
        List<ModifyRequest> requests = mockRequest(true);
        when(transactionTemplate.execute(any(TransactionCallback.class))).then(invocationOnMock -> {
            TransactionCallback callback = invocationOnMock.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        when(jdbcTemplate.update(TEST_SQL, ARGS)).thenReturn(1);
        assertTrue(baseDatabaseOperate.update(transactionTemplate, jdbcTemplate, requests, consumer));
        verify(consumer).accept(eq(Boolean.TRUE), eq(null));
    }
    
    @Test
    void testUpdateFailedWithConsumerAndRollback() {
        List<ModifyRequest> requests = mockRequest(true);
        when(transactionTemplate.execute(any(TransactionCallback.class))).then(invocationOnMock -> {
            TransactionCallback callback = invocationOnMock.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        when(jdbcTemplate.update(TEST_SQL, ARGS)).thenReturn(0);
        assertFalse(baseDatabaseOperate.update(transactionTemplate, jdbcTemplate, requests, consumer));
        verify(consumer).accept(eq(Boolean.FALSE), any(IllegalTransactionStateException.class));
    }
    
    @Test
    void testUpdateFailedWithConsumerAndBadSqlException() {
        List<ModifyRequest> requests = mockRequest(false);
        when(transactionTemplate.execute(any(TransactionCallback.class))).then(invocationOnMock -> {
            TransactionCallback callback = invocationOnMock.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        when(jdbcTemplate.update(TEST_SQL, ARGS)).thenThrow(
                new BadSqlGrammarException("test", TEST_SQL, new SQLException("test")));
        assertFalse(baseDatabaseOperate.update(transactionTemplate, jdbcTemplate, requests, consumer));
        verify(consumer).accept(eq(Boolean.FALSE), any(BadSqlGrammarException.class));
    }
    
    @Test
    void testUpdateWithConsumerAndBadSqlException() {
        List<ModifyRequest> requests = mockRequest(false);
        when(transactionTemplate.execute(any(TransactionCallback.class))).then(invocationOnMock -> {
            TransactionCallback callback = invocationOnMock.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        when(jdbcTemplate.update(TEST_SQL, ARGS)).thenThrow(
                new BadSqlGrammarException("test", TEST_SQL, new SQLException("test")));
        assertFalse(baseDatabaseOperate.update(transactionTemplate, jdbcTemplate, requests, consumer));
        verify(consumer).accept(eq(Boolean.FALSE), any(BadSqlGrammarException.class));
    }
    
    @Test
    void testUpdateWithConsumerAndCannotGetJdbcConnectionException() {
        List<ModifyRequest> requests = mockRequest(false);
        when(transactionTemplate.execute(any(TransactionCallback.class))).then(invocationOnMock -> {
            TransactionCallback callback = invocationOnMock.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        when(jdbcTemplate.update(TEST_SQL, ARGS)).thenThrow(new CannotGetJdbcConnectionException("test"));
        assertThrows(CannotGetJdbcConnectionException.class,
                () -> baseDatabaseOperate.update(transactionTemplate, jdbcTemplate, requests, consumer));
        verify(consumer, never()).accept(any(), any());
    }
    
    @Test
    void testUpdateWithConsumerAndDataAccessException() {
        List<ModifyRequest> requests = mockRequest(false);
        when(transactionTemplate.execute(any(TransactionCallback.class))).then(invocationOnMock -> {
            TransactionCallback callback = invocationOnMock.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        when(jdbcTemplate.update(TEST_SQL, ARGS)).thenThrow(new NJdbcException("test"));
        assertThrows(NJdbcException.class,
                () -> baseDatabaseOperate.update(transactionTemplate, jdbcTemplate, requests, consumer));
        verify(consumer, never()).accept(any(), any());
    }
    
    @Test
    void testUpdateSuccessWithoutConsumer() {
        List<ModifyRequest> requests = mockRequest(false);
        when(transactionTemplate.execute(any(TransactionCallback.class))).then(invocationOnMock -> {
            TransactionCallback callback = invocationOnMock.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        when(jdbcTemplate.update(TEST_SQL, ARGS)).thenReturn(1);
        assertTrue(baseDatabaseOperate.update(transactionTemplate, jdbcTemplate, requests, null));
    }
    
    @Test
    void testUpdateFailedWithoutConsumerAndRollback() {
        List<ModifyRequest> requests = mockRequest(true);
        when(transactionTemplate.execute(any(TransactionCallback.class))).then(invocationOnMock -> {
            TransactionCallback callback = invocationOnMock.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        when(jdbcTemplate.update(TEST_SQL, ARGS)).thenReturn(0);
        assertFalse(baseDatabaseOperate.update(transactionTemplate, jdbcTemplate, requests, null));
    }
    
    @Test
    void testUpdateWithoutConsumerAndDataIntegrityViolationException() {
        List<ModifyRequest> requests = mockRequest(false);
        when(transactionTemplate.execute(any(TransactionCallback.class))).then(invocationOnMock -> {
            TransactionCallback callback = invocationOnMock.getArgument(0, TransactionCallback.class);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        when(jdbcTemplate.update(TEST_SQL, ARGS)).thenThrow(new DataIntegrityViolationException("test"));
        assertFalse(baseDatabaseOperate.update(transactionTemplate, jdbcTemplate, requests, null));
    }
    
    private List<ModifyRequest> mockRequest(boolean rollback) {
        ModifyRequest modifyRequest1 = new ModifyRequest();
        modifyRequest1.setSql(TEST_SQL);
        modifyRequest1.setArgs(ARGS);
        modifyRequest1.setRollBackOnUpdateFail(rollback);
        List<ModifyRequest> modifyRequests = new ArrayList<>();
        modifyRequests.add(modifyRequest1);
        return modifyRequests;
    }
}