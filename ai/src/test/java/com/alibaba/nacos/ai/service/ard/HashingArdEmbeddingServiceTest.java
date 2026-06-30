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

package com.alibaba.nacos.ai.service.ard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link HashingArdEmbeddingService}.
 *
 * @author nacos
 */
class HashingArdEmbeddingServiceTest {
    
    @Test
    void embedShouldBeDeterministicAndNormalized() {
        HashingArdEmbeddingService service = new HashingArdEmbeddingService();
        
        double[] first = service.embed("api helper");
        double[] second = service.embed("api helper");
        
        assertEquals(service.dimension(), first.length);
        assertArrayEquals(first, second);
        assertTrue(norm(first) > 0.99D);
        assertTrue(norm(first) < 1.01D);
    }
    
    private double norm(double[] vector) {
        double value = 0D;
        for (double each : vector) {
            value += each * each;
        }
        return Math.sqrt(value);
    }
}
