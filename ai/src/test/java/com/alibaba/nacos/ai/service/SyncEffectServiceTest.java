/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.config.server.model.form.ConfigForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncEffectServiceTest {
    
    private AtomicBoolean invokeMark;
    
    private SyncEffectService syncEffectService;
    
    @BeforeEach
    void setUp() {
        invokeMark = new AtomicBoolean();
        syncEffectService = new MockSyncEffectService();
    }
    
    @Test
    void toSync() {
        syncEffectService.toSync(new ConfigForm(), System.currentTimeMillis());
        assertTrue(invokeMark.get());
    }
    
    private class MockSyncEffectService implements SyncEffectService {
        
        @Override
        public void toSync(ConfigForm configForm, long startTimeStamp, long timeout, TimeUnit timeUnit) {
            invokeMark.set(true);
        }
    }
}