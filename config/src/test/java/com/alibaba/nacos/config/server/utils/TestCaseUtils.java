package com.alibaba.nacos.config.server.utils;

import org.mockito.Mockito;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class TestCaseUtils {
    
    public static TransactionTemplate createMockTransactionTemplate() {
        JdbcTransactionManager transactionManager = Mockito.mock(JdbcTransactionManager.class);
        
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(
                new DefaultTransactionStatus(null, true, true, false, false, null));
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate;
        
    }
}
