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

/**
 * 最简单的读写锁实现。要求加锁和解锁必须成对调用。
 *
 * @author Nacos
 */
public class SimpleReadWriteLock {

    public synchronized boolean tryReadLock() {
        if (isWriteLocked()) {
            return false;
        } else {
            status++;
            return true;
        }
    }

    public synchronized void releaseReadLock() {
        status--;
    }

    public synchronized boolean tryWriteLock() {
        if (!isFree()) {
            return false;
        } else {
            status = -1;
            return true;
        }
    }

    public synchronized void releaseWriteLock() {
        status = 0;
    }

    private boolean isWriteLocked() {
        return status < 0;
    }

    private boolean isFree() {
        return status == 0;
    }

    /**
     * 零表示没有锁；负数表示加写锁；正数表示加读锁，数值表示读锁的个数。
     */
    private int status = 0;
}
