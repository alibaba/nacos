/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * unit test for TopNCounterMetricsContainer.
 *
 * @author <a href="mailto:liuyixiao0821@gmail.com">liuyixiao</a>
 */
public class TopnCounterMetricsContainerTest {
    
    private TopnCounterMetricsContainer topnCounterMetricsContainer;
    
    @Before
    public void setUp() {
        topnCounterMetricsContainer = new TopnCounterMetricsContainer();
    }
    
    @Test
    public void testPut() {
        topnCounterMetricsContainer.put("test");
        Assert.assertEquals(0, topnCounterMetricsContainer.get("test"));
        topnCounterMetricsContainer.put("test1", 1);
        Assert.assertEquals(1, topnCounterMetricsContainer.get("test1"));
    }
    
    @Test
    public void testIncrement() {
        topnCounterMetricsContainer.put("test", 0);
        topnCounterMetricsContainer.increment("test");
        Assert.assertEquals(1, topnCounterMetricsContainer.get("test"));
    }
    
    @Test
    public void testRemove() {
        topnCounterMetricsContainer.put("test");
        Assert.assertEquals(0, topnCounterMetricsContainer.get("test"));
        topnCounterMetricsContainer.remove("test");
        Assert.assertEquals(-1, topnCounterMetricsContainer.get("test"));
    }
    
    @Test
    public void testRemoveAll() {
        topnCounterMetricsContainer.put("test");
        topnCounterMetricsContainer.put("test1");
        topnCounterMetricsContainer.put("test2");
        topnCounterMetricsContainer.removeAll();
        Assert.assertEquals(-1, topnCounterMetricsContainer.get("test"));
        Assert.assertEquals(-1, topnCounterMetricsContainer.get("test1"));
        Assert.assertEquals(-1, topnCounterMetricsContainer.get("test2"));
    }
    
    @Test
    public void testGetTopNCounterAndRemoveAll() {
        final int N = 10;
        String dataIds = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        List<Pair<String, AtomicInteger>> dataList = new ArrayList<>();
        for (int i = 0; i < dataIds.length(); i++) {
            topnCounterMetricsContainer.put(dataIds.substring(i, i + 1));
            dataList.add(new Pair<>(dataIds.substring(i, i + 1), new AtomicInteger()));
        }
        Random random = new Random();
        for (int i = 0; i < 10000; i++) {
            int j = random.nextInt(dataIds.length());
            topnCounterMetricsContainer.increment(dataIds.substring(j, j + 1));
            dataList.get(j).getSecond().incrementAndGet();
        }
        boolean right = true;
        Collections.sort(dataList, (a, b) -> b.getSecond().get() - a.getSecond().get());
        List<Pair<String, AtomicInteger>> result = topnCounterMetricsContainer.getTopNCounter(N);
        for (Pair<String, AtomicInteger> item : result) {
            // ensure every top N count is greater than (N+1)th greatest.
            if (item.getSecond().get() < dataList.get(N).getSecond().get()) {
                right = false;
                break;
            }
        }
        Assert.assertTrue(right);
        topnCounterMetricsContainer.removeAll();
        for (int i = 0; i < dataIds.length(); i++) {
            Assert.assertEquals(-1, topnCounterMetricsContainer.get(dataIds.substring(i, i + 1)));
        }
        Assert.assertEquals(0, topnCounterMetricsContainer.getTopNCounter(N).size());
    }
}
