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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
public class SimpleReadWriteLockTest {

    @Test
    public void test_双重读锁_全部释放_加写锁() {
        SimpleReadWriteLock lock = new SimpleReadWriteLock();
        assertTrue(lock.tryReadLock());
        assertTrue(lock.tryReadLock());

        lock.releaseReadLock();
        lock.releaseReadLock();

        assertTrue(lock.tryWriteLock());
    }

    @Test
    public void test_加写锁() {
        SimpleReadWriteLock lock = new SimpleReadWriteLock();
        assertTrue(lock.tryWriteLock());
        lock.releaseWriteLock();
    }

    @Test
    public void test_双重写锁() {
        SimpleReadWriteLock lock = new SimpleReadWriteLock();

        assertTrue(lock.tryWriteLock());
        assertFalse(lock.tryWriteLock());
    }

    @Test
    public void test_先读锁后写锁() {
        SimpleReadWriteLock lock = new SimpleReadWriteLock();

        assertTrue(lock.tryReadLock());
        assertFalse(lock.tryWriteLock());
    }

    @Test
    public void test_双重读锁_释放一个_加写锁失败() {
        SimpleReadWriteLock lock = new SimpleReadWriteLock();
        assertTrue(lock.tryReadLock());
        assertTrue(lock.tryReadLock());

        lock.releaseReadLock();

        assertFalse(lock.tryWriteLock());
    }
}
