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
package com.alibaba.nacos.core.distributed.id;

import com.alibaba.nacos.consistency.IdGenerator;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.SpringUtils;

/**
 * // TODO 基于文件的美团Leaf实现
 *
 * @author <a href="mailto:liaochunyhm@live.com">liaochuntao</a>
 */
public class DefaultIdGenerator implements IdGenerator {

    private DefaultIdStore idStore;

    // tow buffer

    private static final double SWAP_CRITICAL_VALUE = 0.1D;

    private volatile long[] bufferOne = new long[2];
    private volatile long[] bufferTwo = new long[2];

    private long currentId;
    private boolean bufferIndex = true;
    private volatile boolean inAcquire = false;

    private String resource;

    public DefaultIdGenerator(String resource) {
        this.resource = resource;
    }

    @Override
    public void init() {
        idStore = SpringUtils.getBean(DefaultIdStore.class);
        idStore.acquireNewIdSequence(resource, Integer.MAX_VALUE, this);
    }

    // TODO 两个 buffer 都用完的情况需要处理

    @Override
    public synchronized long nextId() {
        currentId ++;
        long[] buffer = choose();
        if (currentId > buffer[0]) {
            transfer();
            currentId = choose()[0];
        }
        if (needToAcquire(currentId, choose())) {
            inAcquire = true;
            doAcquire();
        }
        return currentId;
    }

    public long[] choose() {
        return bufferIndex ? bufferOne : bufferTwo;
    }

    public long[] another() {
        return bufferIndex ? bufferTwo : bufferOne;
    }

    public void transfer() {
        bufferIndex = !bufferIndex;
    }

    private boolean needToAcquire(long currentId, long[] bufferUse) {
        return  (currentId * 1.0D - bufferUse[0] + 1) / (bufferUse[1] * 1.0D - bufferUse[0] + 1) > SWAP_CRITICAL_VALUE;
    }

    public void update(long[] newBuffer) {
        if (bufferIndex) {
            bufferTwo = newBuffer;
        } else {
            bufferOne = newBuffer;
        }
    }

    private void doAcquire() {
        GlobalExecutor.executeByCommon(() -> {
            idStore.acquireNewIdSequence(resource, Integer.MAX_VALUE, this);
            inAcquire = false;
        });
    }
}
