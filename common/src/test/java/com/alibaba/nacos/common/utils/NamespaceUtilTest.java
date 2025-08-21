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

import com.alibaba.nacos.api.common.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * test NamespaceUtil.
 *
 * @author klw(213539 @ qq.com)
 * @date 2020/10/13 9:46
 */
class NamespaceUtilTest {
    
    @AfterEach
    void tearDown() {
        NamespaceUtil.setNamespaceDefaultId(Constants.DEFAULT_NAMESPACE_ID);
    }
    
    @Test
    void testProcessTenantParameter() {
        String strPublic = "public";
        assertEquals(strPublic, NamespaceUtil.processNamespaceParameter(strPublic));
        String strEmpty = "";
        String strNull = "null";
        assertEquals(strNull, NamespaceUtil.processNamespaceParameter(strNull));
        assertEquals(strPublic, NamespaceUtil.processNamespaceParameter(strEmpty));
        assertEquals(strPublic, NamespaceUtil.processNamespaceParameter(null));
        String strAbc = "abc";
        assertEquals(strAbc, NamespaceUtil.processNamespaceParameter(strAbc));
        String strdef123 = "def123";
        assertEquals(strdef123, NamespaceUtil.processNamespaceParameter(strdef123));
        String strAbcHasSpace = "  abc  ";
        assertEquals(strAbc, NamespaceUtil.processNamespaceParameter(strAbcHasSpace));
    }
    
    @Test
    void testSetNamespaceDefaultId() {
        assertTrue(NamespaceUtil.isDefaultNamespaceId(Constants.DEFAULT_NAMESPACE_ID));
        NamespaceUtil.setNamespaceDefaultId("Deprecated");
        assertEquals("Deprecated", NamespaceUtil.getNamespaceDefaultId());
        assertFalse(NamespaceUtil.isDefaultNamespaceId(Constants.DEFAULT_NAMESPACE_ID));
    }
    
    @Test
    void testIsNeedTransferNamespace() {
        assertTrue(NamespaceUtil.isNeedTransferNamespace(null));
        assertTrue(NamespaceUtil.isNeedTransferNamespace(""));
        assertFalse(NamespaceUtil.isNeedTransferNamespace("public"));
    }
}
