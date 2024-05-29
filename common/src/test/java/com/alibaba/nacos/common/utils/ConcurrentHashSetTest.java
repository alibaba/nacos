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

import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConcurrentHashSet Test.
 *
 * @ClassName: ConcurrentHashSetTest
 * @Author: ChenHao26
 * @Date: 2022/8/22 11:21
 */
class ConcurrentHashSetTest {
    
    @Test
    void testBasicOps() {
        Set<Integer> set = new ConcurrentHashSet<>();
        
        // addition
        assertTrue(set.add(0));
        assertTrue(set.add(1));
        assertTrue(set.contains(0));
        assertTrue(set.contains(1));
        assertFalse(set.contains(-1));
        assertEquals(2, set.size());
        
        // iter
        for (int i : set) {
            assertTrue(i == 0 || i == 1);
        }
        
        // removal
        assertTrue(set.remove(0));
        assertFalse(set.remove(0));
        assertFalse(set.contains(0));
        assertTrue(set.contains(1));
        assertEquals(1, set.size());
        
        // clear
        assertFalse(set.isEmpty());
        set.clear();
        assertEquals(0, set.size());
        assertTrue(set.isEmpty());
    }
    
    @Test
    void testMultiThread() throws Exception {
        int count = 5;
        SetMultiThreadChecker hashSetChecker = new SetMultiThreadChecker(new HashSet<>());
        hashSetChecker.start();
        while (!hashSetChecker.hasConcurrentError() && hashSetChecker.isRunning()) {
            TimeUnit.SECONDS.sleep(1);
            if (count <= 0) {
                hashSetChecker.stop();
            }
            count--;
        }
        assertTrue(hashSetChecker.hasConcurrentError());
        
        count = 5;
        SetMultiThreadChecker concurrentSetChecker = new SetMultiThreadChecker(new ConcurrentHashSet<>());
        concurrentSetChecker.start();
        while (!concurrentSetChecker.hasConcurrentError() && concurrentSetChecker.isRunning()) {
            TimeUnit.SECONDS.sleep(1);
            if (count == 0) {
                concurrentSetChecker.stop();
            }
            count--;
        }
        assertFalse(concurrentSetChecker.hasConcurrentError());
    }
    
    static class SetMultiThreadChecker {
        
        private final AddDataThread addThread;
        
        private final DeleteDataThread deleteThread;
        
        private final IteratorThread iteratorThread;
        
        public SetMultiThreadChecker(Set<Integer> setToCheck) {
            for (int i = 0; i < 1000; i++) {
                setToCheck.add(i);
            }
            this.addThread = new AddDataThread(setToCheck);
            this.deleteThread = new DeleteDataThread(setToCheck);
            this.iteratorThread = new IteratorThread(setToCheck);
        }
        
        public void start() {
            new Thread(addThread).start();
            new Thread(deleteThread).start();
            new Thread(iteratorThread).start();
        }
        
        public boolean hasConcurrentError() {
            return addThread.hasConcurrentError() || deleteThread.hasConcurrentError() || iteratorThread.hasConcurrentError();
        }
        
        public boolean isRunning() {
            return addThread.isRunning() || deleteThread.isRunning() || iteratorThread.isRunning();
        }
        
        public void stop() {
            addThread.stop();
            deleteThread.stop();
            iteratorThread.stop();
        }
        
    }
    
    abstract static class ConcurrentCheckThread implements Runnable {
        
        protected final Set<Integer> hashSet;
        
        protected boolean concurrentError = false;
        
        protected boolean finish = false;
        
        public ConcurrentCheckThread(Set<Integer> hashSet) {
            this.hashSet = hashSet;
        }
        
        public boolean hasConcurrentError() {
            return concurrentError;
        }
        
        public void stop() {
            finish = true;
        }
        
        public boolean isRunning() {
            return !finish;
        }
        
        @Override
        public void run() {
            try {
                while (isRunning()) {
                    process();
                }
            } catch (ConcurrentModificationException e) {
                concurrentError = true;
            } finally {
                finish = true;
            }
        }
        
        protected abstract void process();
    }
    
    //add data thread
    static class AddDataThread extends ConcurrentCheckThread implements Runnable {
        
        public AddDataThread(Set<Integer> hashSet) {
            super(hashSet);
        }
        
        @Override
        protected void process() {
            int random = new Random().nextInt(1000);
            hashSet.add(random);
        }
        
    }
    
    // delete data thread
    static class DeleteDataThread extends ConcurrentCheckThread implements Runnable {
        
        public DeleteDataThread(Set<Integer> hashSet) {
            super(hashSet);
        }
        
        @Override
        protected void process() {
            int random = new Random().nextInt(1000);
            hashSet.remove(random);
        }
        
    }
    
    static class IteratorThread extends ConcurrentCheckThread implements Runnable {
        
        public IteratorThread(Set<Integer> hashSet) {
            super(hashSet);
        }
        
        @Override
        public void run() {
            System.out.println("start -- hashSet.size() : " + hashSet.size());
            Integer f = null;
            try {
                while (isRunning()) {
                    for (Integer i : hashSet) {
                        f = i;
                    }
                }
            } catch (ConcurrentModificationException e) {
                concurrentError = true;
            } finally {
                finish = true;
            }
            System.out.println("finished at " + f);
            System.out.println("end -- hashSet.size() : " + hashSet.size());
        }
        
        @Override
        protected void process() {
        }
    }
}
