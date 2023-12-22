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

package com.alibaba.nacos.client.naming.remote.gprc.redo.data;

import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class InstanceRedoDataTest {
    
    @Test
    public void testEquals() {
        InstanceRedoData redoData1 = new InstanceRedoData("a", "b");
        assertTrue(redoData1.equals(redoData1));
        assertFalse(redoData1.equals(null));
        BatchInstanceRedoData redoData2 = new BatchInstanceRedoData("a", "b");
        assertFalse(redoData1.equals(redoData2));
        InstanceRedoData redoData3 = new InstanceRedoData("a", "b");
        assertTrue(redoData1.equals(redoData3));
    }
    
    @Test
    public void testHashCode() {
        InstanceRedoData redoData1 = new InstanceRedoData("a", "b");
        redoData1.set(new Instance());
        InstanceRedoData redoData2 = new InstanceRedoData("a", "b");
        redoData2.set(new Instance());
        assertEquals(redoData1.hashCode(), redoData2.hashCode());
        redoData2.get().setIp("1.1.1.1");
        assertNotEquals(redoData1.hashCode(), redoData2.hashCode());
    }
}