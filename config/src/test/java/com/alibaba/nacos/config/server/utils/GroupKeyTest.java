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

package com.alibaba.nacos.config.server.utils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
public class GroupKeyTest {
    
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    
    @Test
    public void testParseInvalidGroupKey() {
        String key = "11111+222+333333+444";
        try {
            GroupKey2.parseKey(key);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
        key = "11111+";
        try {
            GroupKey2.parseKey(key);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
        key = "11111%29+222";
        try {
            GroupKey2.parseKey(key);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
        key = "11111%2b+222";
        try {
            GroupKey2.parseKey(key);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
        key = "11111%25+222";
        String[] pair = GroupKey2.parseKey(key);
        Assert.assertEquals("11111%", pair[0]);
        Assert.assertEquals("222", pair[1]);
    }
    
    @Test
    public void testGetKeyByThreeParams() {
        
        // Act
        final String actual = GroupKey.getKey(",", ",", "3");
        
        // Assert result
        Assert.assertEquals(",+,+3", actual);
    }
    
    @Test
    public void testGetKeyByTwoParams() {
        
        // Act
        final String actual = GroupKey.getKey("3", "'");
        
        // Assert result
        Assert.assertEquals("3+'", actual);
    }
    
    @Test
    public void testGetKeyTenantByPlusThreeParams() {
        
        // Act
        final String actual = GroupKey.getKeyTenant("3", "1", ",");
        
        // Assert result
        Assert.assertEquals("3+1+,", actual);
    }
    
    @Test
    public void testGetKeyTenantByPercentThreeParams() {
        
        // Act
        final String actual = GroupKey.getKeyTenant("\u0000\u0000", "%+", null);
        
        // Assert result
        Assert.assertEquals("\u0000\u0000+%25%2B", actual);
    }
    
    @Test
    public void testParseKeyBySingleCharacter() {
        
        // Act
        final String[] actual = GroupKey.parseKey("/");
        
        // Assert result
        Assert.assertArrayEquals(new String[] {null, "/", null}, actual);
    }
    
    @Test
    public void testParseKeyForPlusIllegalArgumentException() {
        
        // Act
        thrown.expect(IllegalArgumentException.class);
        GroupKey.parseKey("+");
        
        // Method is not expected to return due to exception thrown
    }
    
    @Test
    public void testParseKeyForPercentIllegalArgumentException() {
        
        // Act
        thrown.expect(IllegalArgumentException.class);
        GroupKey.parseKey("%%%5\u0000??????????????");
        
        // Method is not expected to return due to exception thrown
    }
    
    @Test
    public void testParseKeyForInvalidStringIndexOutOfBoundsException() {
        
        // Act
        thrown.expect(StringIndexOutOfBoundsException.class);
        GroupKey.parseKey("++%");
        
        // Method is not expected to return due to exception thrown
    }
    
    @Test
    public void testUrlEncodePlus() {
        
        // Arrange
        final StringBuilder sb = new StringBuilder("????");
        
        // Act
        GroupKey.urlEncode("+", sb);
        
        // Assert side effects
        Assert.assertNotNull(sb);
        Assert.assertEquals("????%2B", sb.toString());
    }
    
    @Test
    public void testUrlEncodeByPercent() {
        
        // Arrange
        final StringBuilder sb = new StringBuilder("??????");
        
        // Act
        GroupKey.urlEncode("%", sb);
        
        // Assert side effects
        Assert.assertNotNull(sb);
        Assert.assertEquals("??????%25", sb.toString());
    }
    
    @Test
    public void testUrlEncodeForNullStringBuilder() {
        
        // Act
        thrown.expect(NullPointerException.class);
        GroupKey.urlEncode("+", null);
        
        // Method is not expected to return due to exception thrown
    }
}
