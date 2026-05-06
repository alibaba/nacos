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

package com.alibaba.nacos.api.naming.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

class AbstractFuzzyWatchEventWatcherTest {
    
    AbstractFuzzyWatchEventWatcher fuzzyWatchEventWatcher;
    
    @BeforeEach
    void setUp() {
        fuzzyWatchEventWatcher = new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(FuzzyWatchChangeEvent event) {
            
            }
        };
    }
    
    @Test
    void getExecutor() {
        assertNull(fuzzyWatchEventWatcher.getExecutor());
    }
    
    @Test
    void onPatternOverLimit() {
        assertDoesNotThrow(fuzzyWatchEventWatcher::onPatternOverLimit);
    }
    
    @Test
    void onServiceReachUpLimit() {
        assertDoesNotThrow(fuzzyWatchEventWatcher::onServiceReachUpLimit);
    }
}