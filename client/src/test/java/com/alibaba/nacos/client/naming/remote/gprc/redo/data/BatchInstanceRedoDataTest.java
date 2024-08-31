/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.naming.remote.gprc.redo.data;

import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BatchInstanceRedoDataTest {
    
    @Test
    @SuppressWarnings("all")
    void testEquals() {
        BatchInstanceRedoData redoData1 = new BatchInstanceRedoData("a", "b");
        redoData1.setInstances(Collections.singletonList(new Instance()));
        BatchInstanceRedoData redoData2 = new BatchInstanceRedoData("a", "b");
        redoData2.setInstances(Collections.singletonList(new Instance()));
        assertEquals(redoData1, redoData1);
        assertEquals(redoData1, redoData2);
        redoData2.getInstances().get(0).setIp("1.1.1.1");
        assertNotEquals(null, redoData1);
        assertNotEquals(redoData1, redoData2);
        assertNotEquals(redoData1, redoData2);
        BatchInstanceRedoData redoData3 = new BatchInstanceRedoData("c", "b");
        assertNotEquals(redoData1, redoData3);
    }
    
    @Test
    void testHashCode() {
        BatchInstanceRedoData redoData1 = new BatchInstanceRedoData("a", "b");
        redoData1.setInstances(Collections.singletonList(new Instance()));
        BatchInstanceRedoData redoData2 = new BatchInstanceRedoData("a", "b");
        redoData2.setInstances(Collections.singletonList(new Instance()));
        assertEquals(redoData1.hashCode(), redoData2.hashCode());
        redoData2.getInstances().get(0).setIp("1.1.1.1");
        assertNotEquals(redoData1.hashCode(), redoData2.hashCode());
    }
}