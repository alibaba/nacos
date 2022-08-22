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

package com.alibaba.nacos.common.utils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * ConcurrentHashSet Test.
 * @ClassName: ConcurrentHashSetTest
 * @Author: ChenHao26
 * @Date: 2022/8/22 11:21
 */
public class ConcurrentHashSetTest {
    
    Set<Integer> concurrentHashSet;

    @Before
    public void setUp() {
        concurrentHashSet = new ConcurrentHashSet<>();
        concurrentHashSet.add(1);
        concurrentHashSet.add(2);
        concurrentHashSet.add(3);
        concurrentHashSet.add(4);
        concurrentHashSet.add(5);
        
    }
    
    @Test
    public void size() {
        Assert.assertEquals(concurrentHashSet.size(), 5);
    }
    
    @Test
    public void contains() {
        Assert.assertTrue(concurrentHashSet.contains(1));
    }
    
    @Test
    public void testMultithreaded() {
        try {
            concurrentHashSet = new HashSet<>();
            executeThread();
        } catch (Exception e) {
            Assert.assertTrue(e instanceof ConcurrentModificationException);
        }
        
        try {
            concurrentHashSet = new ConcurrentHashSet<>();
            executeThread();
        } catch (Exception e) {
            Assert.assertNull(e);
        }
    }
    
    /**
     * execute muti thread.
     */
    public void executeThread() throws Exception {
        for (int i = 0; i < 1000; i++) {
            concurrentHashSet.add(i);
        }
    
        new Thread(new AddDataThread(concurrentHashSet)).start();
        new Thread(new DeleteDataThread(concurrentHashSet)).start();
        new Thread(new IteratorThread(concurrentHashSet)).start();
    }
    
    //add data thread
    static class AddDataThread implements Runnable {
        Set<Integer> hashSet;
    
        public AddDataThread(Set<Integer> hashSet) {
            this.hashSet = hashSet;
        }
    
        @Override
        public void run() {
            while (true) {
                int random = new Random().nextInt();
                hashSet.add(random);
            }
        }
    }
    
    // delete data thread
    static class DeleteDataThread implements Runnable {
        Set<Integer> hashSet;
    
        public DeleteDataThread(Set<Integer> hashSet) {
            this.hashSet = hashSet;
        }
    
        @Override
        public void run() {
            int random = new Random().nextInt(1000);
            while (true) {
                hashSet.remove(random);
            }
        }
    }
    
    static class IteratorThread implements Runnable {
    
        Set<Integer> hashSet;
    
        public IteratorThread(Set<Integer> hashSet) {
            this.hashSet = hashSet;
        }
    
        @Override
        public void run() {
            System.out.println("start -- hashSet.size() : " + hashSet.size());
            for (Integer str : hashSet) {
                System.out.println("value : " + str);
            }
            System.out.println("end -- hashSet.size() : " + hashSet.size());
        }
    }
}
