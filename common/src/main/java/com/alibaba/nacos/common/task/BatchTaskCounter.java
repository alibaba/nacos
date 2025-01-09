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

package com.alibaba.nacos.common.task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * batch task counter.
 *
 * @author shiyiyue
 */
public class BatchTaskCounter {
    
    List<AtomicBoolean> batchCounter;
    
    public BatchTaskCounter(int totalBatch) {
        initBatchCounter(totalBatch);
    }
    
    /**
     * init counter.
     * @param totalBatch totalBatch.
     */
    private void initBatchCounter(int totalBatch) {
        batchCounter = new ArrayList<>(totalBatch);
        for (int i = 0; i < totalBatch; i++) {
            batchCounter.add(i, new AtomicBoolean(false));
        }
    }
    
    /**
     * set bath succeed.
     * @param batch succeed batch.
     */
    public void batchSuccess(int batch) {
        if (batch <= batchCounter.size()) {
            batchCounter.get(batch - 1).set(true);
        }
    }
    
    /**
     * check all completed.
     * @return
     */
    public boolean batchCompleted() {
        for (AtomicBoolean atomicBoolean : batchCounter) {
            if (!atomicBoolean.get()) {
                return false;
            }
        }
        return true;
    }
    
    public int getTotalBatch() {
        return batchCounter.size();
    }
}
