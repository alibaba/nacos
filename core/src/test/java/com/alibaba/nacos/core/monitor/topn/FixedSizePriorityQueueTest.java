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

package com.alibaba.nacos.core.monitor.topn;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedSizePriorityQueueTest {
    
    @Test
    void testOfferEmpty() {
        FixedSizePriorityQueue<Integer> queue = new FixedSizePriorityQueue<>(10, Comparator.<Integer>naturalOrder());
        List<Integer> list = queue.toList();
        assertTrue(list.isEmpty());
    }
    
    @Test
    void testOfferLessThanSize() {
        FixedSizePriorityQueue<Integer> queue = new FixedSizePriorityQueue<>(10, Comparator.<Integer>naturalOrder());
        for (int i = 0; i < 5; i++) {
            queue.offer(i);
        }
        List<Integer> list = queue.toList();
        assertEquals(5, list.size());
        for (int i = 0; i < 5; i++) {
            assertTrue(list.contains(i));
        }
    }
    
    @Test
    void testOfferMoreThanSizeWithIncreasing() {
        FixedSizePriorityQueue<Integer> queue = new FixedSizePriorityQueue<>(10, Comparator.<Integer>naturalOrder());
        for (int i = 0; i < 15; i++) {
            queue.offer(i);
        }
        List<Integer> list = queue.toList();
        assertEquals(10, list.size());
        for (int i = 14; i > 4; i--) {
            assertTrue(list.contains(i));
        }
    }
    
    @Test
    void testOfferMoreThanSizeWithDecreasing() {
        FixedSizePriorityQueue<Integer> queue = new FixedSizePriorityQueue<>(10, Comparator.<Integer>naturalOrder());
        for (int i = 14; i > 0; i--) {
            queue.offer(i);
        }
        List<Integer> list = queue.toList();
        assertEquals(10, list.size());
        for (int i = 14; i > 4; i--) {
            assertTrue(list.contains(i));
        }
    }
    
    @Test
    void testOfferMoreThanSizeWithShuffle() {
        List<Integer> testCase = new ArrayList<>(50);
        for (int i = 0; i < 50; i++) {
            testCase.add(i);
        }
        Collections.shuffle(testCase);
        FixedSizePriorityQueue<Integer> queue = new FixedSizePriorityQueue<>(10, Comparator.<Integer>naturalOrder());
        testCase.forEach(queue::offer);
        List<Integer> list = queue.toList();
        assertEquals(10, list.size());
        for (int i = 49; i > 39; i--) {
            assertTrue(list.contains(i));
        }
    }
}