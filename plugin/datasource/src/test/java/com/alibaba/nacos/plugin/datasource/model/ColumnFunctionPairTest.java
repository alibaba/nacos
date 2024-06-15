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

package com.alibaba.nacos.plugin.datasource.model;

import com.alibaba.nacos.plugin.datasource.enums.TrustedSqlFunctionEnum;
import org.junit.Assert;
import org.junit.Test;

/**
 * Column function pair Test.
 *
 * @author blake.qiu
 */
public class ColumnFunctionPairTest {
    @Test
    public void testBuildWithColumn() {
        ColumnFunctionPair columnFunctionPair = ColumnFunctionPair.withColumn("column");
        Assert.assertNotNull(columnFunctionPair);
        Assert.assertEquals("column", columnFunctionPair.getColumn());
        Assert.assertNull(columnFunctionPair.getFunction());
    }

    @Test
    public void testBuildWithColumnAndFunction() {
        ColumnFunctionPair columnFunctionPair = ColumnFunctionPair.withColumnAndFunction("column", null);
        Assert.assertNotNull(columnFunctionPair);
        Assert.assertEquals("column", columnFunctionPair.getColumn());
        Assert.assertNull(columnFunctionPair.getFunction());

        ColumnFunctionPair columnFunctionPair2 = ColumnFunctionPair.withColumnAndFunction("column", TrustedSqlFunctionEnum.CURRENT_TIMESTAMP);
        Assert.assertNotNull(columnFunctionPair2);
        Assert.assertEquals("column", columnFunctionPair.getColumn());
        Assert.assertEquals(TrustedSqlFunctionEnum.CURRENT_TIMESTAMP.getFunction(), columnFunctionPair2.getFunction());

        ColumnFunctionPair columnFunctionPair3 = ColumnFunctionPair.withColumnAndFunction("column", TrustedSqlFunctionEnum.NOW);
        Assert.assertNotNull(columnFunctionPair3);
        Assert.assertEquals("column", columnFunctionPair.getColumn());
        Assert.assertEquals(TrustedSqlFunctionEnum.NOW.getFunction(), columnFunctionPair3.getFunction());
    }
}
