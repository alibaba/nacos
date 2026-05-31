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

import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseOperateTest {
    
    @Spy
    private DatabaseOperate databaseOperate;
    
    @BeforeEach
    void setUp() {
        EmbeddedStorageContextHolder.cleanAllContext();
    }
    
    @AfterEach
    void tearDown() {
        EmbeddedStorageContextHolder.cleanAllContext();
    }
    
    @Test
    void testDefaultUpdate() {
        when(databaseOperate.update(anyList(), eq(null))).thenReturn(true);
        assertTrue(databaseOperate.update(Collections.emptyList()));
    }
    
    @Test
    void testBlockUpdateNoConsumer() {
        when(databaseOperate.update(anyList(), eq(null))).thenReturn(true);
        assertTrue(databaseOperate.blockUpdate());
    }
    
    @Test
    void testBlockUpdateWithConsumer() {
        AtomicBoolean consumed = new AtomicBoolean(false);
        when(databaseOperate.update(anyList(), any())).thenAnswer(invocation -> {
            Object consumer = invocation.getArgument(1);
            if (consumer != null) {
                ((java.util.function.BiConsumer<Boolean, Throwable>) consumer).accept(true, null);
            }
            return true;
        });
        assertTrue(databaseOperate.blockUpdate((success, ex) -> consumed.set(true)));
        assertTrue(consumed.get());
    }
}
