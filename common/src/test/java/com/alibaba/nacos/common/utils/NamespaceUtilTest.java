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

import org.junit.Assert;
import org.junit.Test;

/**
 * test NamespaceUtil.
 *
 * @author klw(213539 @ qq.com)
 * @date 2020/10/13 9:46
 */
public class NamespaceUtilTest {
    
    @Test
    public void testProcessTenantParameter() {
        String strPublic = "public";
        String strNull = "null";
        String strEmpty = "";
        String strAbc = "abc";
        String strdef123 = "def123";
        String strAbcHasSpace = "  abc  ";
        Assert.assertEquals(strEmpty, NamespaceUtil.processNamespaceParameter(strPublic));
        Assert.assertEquals(strEmpty, NamespaceUtil.processNamespaceParameter(strNull));
        Assert.assertEquals(strEmpty, NamespaceUtil.processNamespaceParameter(strEmpty));
        Assert.assertEquals(strEmpty, NamespaceUtil.processNamespaceParameter(null));
        Assert.assertEquals(strAbc, NamespaceUtil.processNamespaceParameter(strAbc));
        Assert.assertEquals(strdef123, NamespaceUtil.processNamespaceParameter(strdef123));
        Assert.assertEquals(strAbc, NamespaceUtil.processNamespaceParameter(strAbcHasSpace));
    }
    
}
