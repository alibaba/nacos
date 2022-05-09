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

package com.alibaba.nacos.common.packagescan;

import com.alibaba.nacos.api.remote.request.Request;
import junit.framework.TestCase;

import java.util.Set;

public class PackageScanTest extends TestCase {

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
    }

    /**
     * testGetSubTypesOf.
     */
    public void testGetSubTypesOf() {
        DefaultPackageScan packageScan = new DefaultPackageScan();
        Set<Class<Request>> subTypesOf = packageScan.getSubTypesOf("com.alibaba.nacos.api.naming.remote.request", Request.class);
        assertTrue(subTypesOf.size() > 0);
    }

}