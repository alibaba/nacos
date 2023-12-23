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

package com.alibaba.nacos.config.server.utils;

import org.mockito.Mockito;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class TestCaseUtils {
    
    /**
     * create mocked transaction template with transact ability.
     *
     * @return
     */
    public static TransactionTemplate createMockTransactionTemplate() {
        JdbcTransactionManager transactionManager = Mockito.mock(JdbcTransactionManager.class);
        
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(
                new DefaultTransactionStatus(null, true, true, false, false, null));
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate;
        
    }
    
    /**
     * create mocked transaction template with transact ability.
     *
     * @return
     */
    public static GeneratedKeyHolder createGeneratedKeyHolder(long wantedId) {
        GeneratedKeyHolder generatedKeyHolder = new GeneratedKeyHolder();
        HashMap<String, Object> objectObjectHashMap = new HashMap<>();
        objectObjectHashMap.put("whatever", wantedId);
        generatedKeyHolder.getKeyList().add(objectObjectHashMap);
        
        return generatedKeyHolder;
    }
}
