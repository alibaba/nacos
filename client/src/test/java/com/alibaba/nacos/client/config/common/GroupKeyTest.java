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

package com.alibaba.nacos.client.config.common;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class GroupKeyTest {
    
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    
    @Test
    public void testGetKey() {
        Assert.assertEquals("1+foo", GroupKey.getKey("1", "foo"));
        Assert.assertEquals("1+foo+bar", GroupKey.getKey("1", "foo", "bar"));
        Assert.assertEquals("1+f%2Boo+b%25ar", GroupKey.getKey("1", "f+oo", "b%ar"));
    }
    
    @Test
    public void testGetKeyTenant() {
        Assert.assertEquals("1+foo+bar", GroupKey.getKeyTenant("1", "foo", "bar"));
    }
    
    @Test
    public void testParseKey() {
        Assert.assertArrayEquals(new String[] {"a", "f+oo", null}, GroupKey.parseKey("a+f%2Boo"));
        Assert.assertArrayEquals(new String[] {"b", "f%oo", null}, GroupKey.parseKey("b+f%25oo"));
        Assert.assertArrayEquals(new String[] {"a", "b", "c"}, GroupKey.parseKey("a+b+c"));
    }
    
    @Test
    public void testParseKeyIllegalArgumentException1() {
        thrown.expect(IllegalArgumentException.class);
        GroupKey.parseKey("");
    }
    
    @Test
    public void testParseKeyIllegalArgumentException2() {
        thrown.expect(IllegalArgumentException.class);
        GroupKey.parseKey("f%oo");
    }
    
    @Test
    public void testParseKeyIllegalArgumentException3() {
        thrown.expect(IllegalArgumentException.class);
        GroupKey.parseKey("f+o+o+bar");
    }
    
    @Test
    public void testParseKeyIllegalArgumentException4() {
        thrown.expect(IllegalArgumentException.class);
        GroupKey.parseKey("f++bar");
    }
    
    @Test
    public void testGetKeyDatIdParam() {
        thrown.expect(IllegalArgumentException.class);
        GroupKey.getKey("", "a");
    }
    
    @Test
    public void testGetKeyGroupParam() {
        thrown.expect(IllegalArgumentException.class);
        GroupKey.getKey("a", "");
    }
}
