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
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alibaba.nacos.core.utils.ThreadUtils;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:liaochunyhm@live.com">liaochuntao</a>
 */
public class DefaultIdGenerator implements IdGenerator {

    private static final double SWAP_CRITICAL_VALUE = 0.1D;

    private DefaultIdStore idStore;

    // tow buffer

    private volatile long[] bufferOne = new long[]{-1L, -1L};
    private volatile long[] bufferTwo = new long[]{-1L, -1L};

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

        // The first request requires an asynchronous request

        idStore.firstAcquire(resource, Integer.MAX_VALUE, this);
    }

    @Override
    public long currentId() {
        return currentId;
    }

    // TODO 两个 buffer 都用完的情况需要处理

    @Override
    public synchronized long nextId() {
        long tmp = currentId + 1;
        long[] buffer = current();
        if (tmp > buffer[1]) {

            // The currently used buffer has been used up, and the standby buffer was not applied successfully

            if (inAcquire) {
                int waitCnt = 5;
                for (; ; ) {
                    waitCnt--;
                    if (waitCnt < 0) {
                        if (inAcquire) {
                            throw new AcquireIdException("[" + resource + "] ID resource application failed");
                        } else {
                            break;
                        }
                    }
                    ThreadUtils.sleep(10);
                    Loggers.ID_GENERATOR.warn("[{}] The current ID buffer has been used up and is being applied", resource);
                }
            }

            swap();
            tmp = current()[0];
        }
        if (needToAcquire(tmp, current())) {
            inAcquire = true;
            doAcquire();
        }
        currentId = tmp;
        return currentId;
    }

    @Override
    public Map<Object, Object> info() {
        Map<Object, Object> info = new HashMap<>(8);
        info.put("currentId", currentId);
        info.put("bufferOneStart", current()[0]);
        info.put("bufferOneEnd", current()[1]);
        info.put("bufferTwoStart", another()[0]);
        info.put("bufferTwoEnd", another()[1]);
        return info;
    }

    private long[] current() {
        return bufferIndex ? bufferOne : bufferTwo;
    }

    private long[] another() {
        return bufferIndex ? bufferTwo : bufferOne;
    }

    private void swap() {
        bufferIndex = !bufferIndex;
    }

    private boolean needToAcquire(long currentId, long[] bufferUse) {
        return (currentId * 1.0D - bufferUse[0] + 1) / (bufferUse[1] * 1.0D - bufferUse[0] + 1) > SWAP_CRITICAL_VALUE;
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
