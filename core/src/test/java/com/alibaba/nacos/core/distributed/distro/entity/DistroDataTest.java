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

package com.alibaba.nacos.core.distributed.distro.entity;

import com.alibaba.nacos.consistency.DataOperation;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;

public class DistroDataTest extends TestCase {
    
    private DistroData distroData;
    
    private DistroKey distroKey;
    
    private final String type = "com.alibaba.nacos.naming.iplist.";
    
    private int contentSize = 10;
    
    private final byte[] content = new byte[contentSize];
    
    @Before
    public void setUp() {
        distroKey = new DistroKey("checksum", type);
        distroData = new DistroData(distroKey, content);
        distroData.setType(DataOperation.VERIFY);
    }
    
    @Test
    public void testGetters() {
        assertEquals(distroKey, distroData.getDistroKey());
        assertEquals(content, distroData.getContent());
        assertEquals(DataOperation.VERIFY, distroData.getType());
    }
}