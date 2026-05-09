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

package com.alibaba.nacos.common.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for BatchTaskCounter.
 *
 * @author nacos
 */
class BatchTaskCounterTest {
    
    @Test
    @DisplayName("Constructor should initialize batch counter with correct size")
    void testConstructorShouldInitializeBatchCounter() {
        BatchTaskCounter counter = new BatchTaskCounter(5);
        assertEquals(5, counter.getTotalBatch());
    }
    
    @Test
    @DisplayName("batchSuccess should set batch to true")
    void testBatchSuccessShouldSetBatchToTrue() {
        BatchTaskCounter counter = new BatchTaskCounter(3);
        counter.batchSuccess(1);
        assertFalse(counter.batchCompleted());
        
        counter.batchSuccess(2);
        counter.batchSuccess(3);
        assertTrue(counter.batchCompleted());
    }
    
    @Test
    @DisplayName("batchSuccess with invalid batch should not throw")
    void testBatchSuccessWithInvalidBatchShouldNotThrow() {
        BatchTaskCounter counter = new BatchTaskCounter(2);
        // batch > size is handled gracefully by source code
        // batch = 0 would cause IndexOutOfBounds, so we only test batch > size
        counter.batchSuccess(10);
        assertFalse(counter.batchCompleted());
    }
    
    @Test
    @DisplayName("batchCompleted when all true should return true")
    void testBatchCompletedWhenAllTrueShouldReturnTrue() {
        BatchTaskCounter counter = new BatchTaskCounter(3);
        counter.batchSuccess(1);
        counter.batchSuccess(2);
        counter.batchSuccess(3);
        assertTrue(counter.batchCompleted());
    }
    
    @Test
    @DisplayName("batchCompleted when some false should return false")
    void testBatchCompletedWhenSomeFalseShouldReturnFalse() {
        BatchTaskCounter counter = new BatchTaskCounter(3);
        counter.batchSuccess(1);
        // batch 2 and 3 not succeeded
        assertFalse(counter.batchCompleted());
    }
    
    @Test
    @DisplayName("getTotalBatch should return correct size")
    void testGetTotalBatchShouldReturnCorrectSize() {
        BatchTaskCounter counter = new BatchTaskCounter(10);
        assertEquals(10, counter.getTotalBatch());
    }
}
