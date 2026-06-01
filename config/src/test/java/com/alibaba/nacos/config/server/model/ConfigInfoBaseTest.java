/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigInfoBaseTest {
    
    @Test
    void testDefaultConstructor() {
        ConfigInfoBase base = new ConfigInfoBase();
        assertEquals(0, base.getId());
        assertNull(base.getDataId());
        assertNull(base.getGroup());
        assertNull(base.getContent());
        assertNull(base.getMd5());
        assertNull(base.getEncryptedDataKey());
    }
    
    @Test
    void testParameterizedConstructor() {
        ConfigInfoBase base = new ConfigInfoBase("dataId", "group", "content");
        assertEquals("dataId", base.getDataId());
        assertEquals("group", base.getGroup());
        assertEquals("content", base.getContent());
        assertNotNull(base.getMd5());
    }
    
    @Test
    void testConstructorWithNullContent() {
        ConfigInfoBase base = new ConfigInfoBase("dataId", "group", null);
        assertNull(base.getMd5());
    }
    
    @Test
    void testSettersAndGetters() {
        ConfigInfoBase base = new ConfigInfoBase();
        base.setId(1L);
        base.setDataId("dataId");
        base.setGroup("group");
        base.setContent("content");
        base.setMd5("md5");
        base.setEncryptedDataKey("key");
        
        assertEquals(1L, base.getId());
        assertEquals("dataId", base.getDataId());
        assertEquals("group", base.getGroup());
        assertEquals("content", base.getContent());
        assertEquals("md5", base.getMd5());
        assertEquals("key", base.getEncryptedDataKey());
    }
    
    @Test
    void testDump() {
        ConfigInfoBase base = new ConfigInfoBase("dataId", "group", "content");
        StringWriter sw = new StringWriter();
        PrintWriter writer = new PrintWriter(sw);
        base.dump(writer);
        writer.flush();
        assertEquals("content", sw.toString());
    }
    
    @Test
    void testCompareToNull() {
        ConfigInfoBase base = new ConfigInfoBase("dataId", "group", "content");
        assertEquals(1, base.compareTo(null));
    }
    
    @Test
    void testCompareToBothDataIdNull() {
        ConfigInfoBase a = new ConfigInfoBase();
        ConfigInfoBase b = new ConfigInfoBase();
        assertEquals(0, a.compareTo(b));
    }
    
    @Test
    void testCompareToThisDataIdNull() {
        ConfigInfoBase a = new ConfigInfoBase();
        ConfigInfoBase b = new ConfigInfoBase("dataId", "group", "content");
        assertEquals(-1, a.compareTo(b));
    }
    
    @Test
    void testCompareToOtherDataIdNull() {
        ConfigInfoBase a = new ConfigInfoBase("dataId", "group", "content");
        ConfigInfoBase b = new ConfigInfoBase();
        assertEquals(1, a.compareTo(b));
    }
    
    @Test
    void testCompareToDataIdDifferent() {
        ConfigInfoBase a = new ConfigInfoBase("a", "group", "content");
        ConfigInfoBase b = new ConfigInfoBase("b", "group", "content");
        assertTrue(a.compareTo(b) < 0);
    }
    
    @Test
    void testCompareToSameDataIdBothGroupNull() {
        ConfigInfoBase a = new ConfigInfoBase();
        a.setDataId("dataId");
        ConfigInfoBase b = new ConfigInfoBase();
        b.setDataId("dataId");
        assertEquals(0, a.compareTo(b));
    }
    
    @Test
    void testCompareToSameDataIdThisGroupNull() {
        ConfigInfoBase a = new ConfigInfoBase();
        a.setDataId("dataId");
        ConfigInfoBase b = new ConfigInfoBase("dataId", "group", "content");
        assertEquals(-1, a.compareTo(b));
    }
    
    @Test
    void testCompareToSameDataIdOtherGroupNull() {
        ConfigInfoBase a = new ConfigInfoBase("dataId", "group", "content");
        ConfigInfoBase b = new ConfigInfoBase();
        b.setDataId("dataId");
        assertEquals(1, a.compareTo(b));
    }
    
    @Test
    void testCompareToGroupDifferent() {
        ConfigInfoBase a = new ConfigInfoBase("dataId", "a", "content");
        ConfigInfoBase b = new ConfigInfoBase("dataId", "b", "content");
        assertTrue(a.compareTo(b) < 0);
    }
    
    @Test
    void testCompareToSameDataIdGroupBothContentNull() {
        ConfigInfoBase a = new ConfigInfoBase("dataId", "group", null);
        ConfigInfoBase b = new ConfigInfoBase("dataId", "group", null);
        assertEquals(0, a.compareTo(b));
    }
    
    @Test
    void testCompareToSameDataIdGroupThisContentNull() {
        ConfigInfoBase a = new ConfigInfoBase("dataId", "group", null);
        ConfigInfoBase b = new ConfigInfoBase("dataId", "group", "content");
        assertEquals(-1, a.compareTo(b));
    }
    
    @Test
    void testCompareToSameDataIdGroupOtherContentNull() {
        ConfigInfoBase a = new ConfigInfoBase("dataId", "group", "content");
        ConfigInfoBase b = new ConfigInfoBase("dataId", "group", null);
        assertEquals(1, a.compareTo(b));
    }
    
    @Test
    void testCompareToContentDifferent() {
        ConfigInfoBase a = new ConfigInfoBase("dataId", "group", "a");
        ConfigInfoBase b = new ConfigInfoBase("dataId", "group", "b");
        assertTrue(a.compareTo(b) < 0);
    }
    
    @Test
    void testCompareToAllEqual() {
        ConfigInfoBase a = new ConfigInfoBase("dataId", "group", "content");
        ConfigInfoBase b = new ConfigInfoBase("dataId", "group", "content");
        assertEquals(0, a.compareTo(b));
    }
    
    @Test
    void testEqualsSameObject() {
        ConfigInfoBase base = new ConfigInfoBase("dataId", "group", "content");
        assertEquals(base, base);
    }
    
    @Test
    void testEqualsNull() {
        ConfigInfoBase base = new ConfigInfoBase("dataId", "group", "content");
        assertFalse(base.equals(null));
        assertNotEquals(null, base);
    }
    
    @Test
    void testEqualsDifferentClass() {
        ConfigInfoBase base = new ConfigInfoBase("dataId", "group", "content");
        assertNotEquals("string", base);
    }
    
    @Test
    void testEqualsContentNullVsNonNull() {
        ConfigInfoBase a = new ConfigInfoBase();
        ConfigInfoBase b = new ConfigInfoBase();
        b.setContent("content");
        assertNotEquals(a, b);
    }
    
    @Test
    void testEqualsContentDifferent() {
        ConfigInfoBase a = new ConfigInfoBase("dataId", "group", "a");
        ConfigInfoBase b = new ConfigInfoBase("dataId", "group", "b");
        assertNotEquals(a, b);
    }
    
    @Test
    void testEqualsDataIdDifferent() {
        ConfigInfoBase a = new ConfigInfoBase("dataId1", "group", "content");
        ConfigInfoBase b = new ConfigInfoBase("dataId2", "group", "content");
        assertFalse(a.equals(b));
        assertNotEquals(a, b);
    }
    
    @Test
    void testEqualsDataIdNullVsNonNull() {
        ConfigInfoBase a = new ConfigInfoBase();
        a.setContent("content");
        a.setGroup("group");
        ConfigInfoBase b = new ConfigInfoBase();
        b.setContent("content");
        b.setGroup("group");
        b.setDataId("dataId");
        assertFalse(a.equals(b));
        assertNotEquals(a, b);
    }
    
    @Test
    void testEqualsGroupNullVsNonNull() {
        ConfigInfoBase a = new ConfigInfoBase();
        a.setDataId("dataId");
        a.setContent("content");
        ConfigInfoBase b = new ConfigInfoBase();
        b.setDataId("dataId");
        b.setContent("content");
        b.setGroup("group");
        assertFalse(a.equals(b));
        assertNotEquals(a, b);
    }
    
    @Test
    void testEqualsGroupDifferent() {
        ConfigInfoBase a = new ConfigInfoBase("dataId", "group1", "content");
        ConfigInfoBase b = new ConfigInfoBase("dataId", "group2", "content");
        assertFalse(a.equals(b));
        assertNotEquals(a, b);
    }
    
    @Test
    void testEqualsMd5NullVsNonNull() {
        ConfigInfoBase a = new ConfigInfoBase();
        a.setDataId("dataId");
        a.setGroup("group");
        a.setContent("content");
        ConfigInfoBase b = new ConfigInfoBase();
        b.setDataId("dataId");
        b.setGroup("group");
        b.setContent("content");
        b.setMd5("md5");
        assertNotEquals(a, b);
    }
    
    @Test
    void testEqualsMd5Different() {
        ConfigInfoBase a = new ConfigInfoBase("dataId", "group", "content");
        ConfigInfoBase b = new ConfigInfoBase("dataId", "group", "content");
        b.setMd5("different");
        assertNotEquals(a, b);
    }
    
    @Test
    void testEqualsBothContentNull() {
        ConfigInfoBase a = new ConfigInfoBase();
        a.setDataId("dataId");
        a.setGroup("group");
        ConfigInfoBase b = new ConfigInfoBase();
        b.setDataId("dataId");
        b.setGroup("group");
        assertEquals(a, b);
    }
    
    @Test
    void testHashCode() {
        ConfigInfoBase a = new ConfigInfoBase("dataId", "group", "content");
        ConfigInfoBase b = new ConfigInfoBase("dataId", "group", "content");
        assertEquals(a.hashCode(), b.hashCode());
    }
    
    @Test
    void testToString() {
        ConfigInfoBase base = new ConfigInfoBase("dataId", "group", "content");
        base.setId(1L);
        String str = base.toString();
        assertTrue(str.contains("dataId"));
        assertTrue(str.contains("group"));
        assertTrue(str.contains("content"));
    }
}
