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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
class GroupKey2Test {
    
    @Test
    void testParseInvalidGroupKey2() {
        String key = "11111+222+333333+444";
        try {
            GroupKey2.parseKey(key);
            fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
        key = "11111+";
        try {
            GroupKey2.parseKey(key);
            fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
        key = "11111%29+222";
        try {
            GroupKey2.parseKey(key);
            fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
        key = "11111%2b+222";
        try {
            GroupKey2.parseKey(key);
            fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
        key = "11111%25+222";
        String[] pair = GroupKey2.parseKey(key);
        assertEquals("11111%", pair[0]);
        assertEquals("222", pair[1]);
    }
    
    @Test
    void testGetKeyByThreeParams() {
        
        // Act
        final String actual = GroupKey2.getKey(",", ",", "3");
        
        // Assert result
        assertEquals(",+,+3", actual);
    }
    
    @Test
    void testGetKeyByTwoParams() {
        
        // Act
        final String actual = GroupKey2.getKey("3", "'");
        
        // Assert result
        assertEquals("3+'", actual);
    }
    
    @Test
    void testParseKeyBySingleCharacter() {
        
        // Act
        final String[] actual = GroupKey2.parseKey("/");
        
        // Assert result
        assertArrayEquals(new String[] {null, "/", null}, actual);
    }
    
    @Test
    void testParseKeyForPlusIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            GroupKey2.parseKey("+");
            
            // Method is not expected to return due to exception thrown
        });
        
        // Method is not expected to return due to exception thrown
    }
    
    @Test
    void testParseKeyForPercentIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            GroupKey2.parseKey("%%%5\u0000??????????????");
            
            // Method is not expected to return due to exception thrown
        });
        
        // Method is not expected to return due to exception thrown
    }
    
    @Test
    void testParseKeyForInvalidStringIndexOutOfBoundsException() {
        assertThrows(StringIndexOutOfBoundsException.class, () -> {
            GroupKey2.parseKey("++%");
            
            // Method is not expected to return due to exception thrown
        });
        
        // Method is not expected to return due to exception thrown
    }
    
    @Test
    void testUrlEncodePlus() {
        
        // Arrange
        final StringBuilder sb = new StringBuilder("????");
        
        // Act
        GroupKey2.urlEncode("+", sb);
        
        // Assert side effects
        assertNotNull(sb);
        assertEquals("????%2B", sb.toString());
    }
    
    @Test
    void testUrlEncodeByPercent() {
        
        // Arrange
        final StringBuilder sb = new StringBuilder("??????");
        
        // Act
        GroupKey2.urlEncode("%", sb);
        
        // Assert side effects
        assertNotNull(sb);
        assertEquals("??????%25", sb.toString());
    }
    
    @Test
    void testUrlEncodeForNullStringBuilder() {
        assertThrows(NullPointerException.class, () -> {
            GroupKey2.urlEncode("+", null);
            
            // Method is not expected to return due to exception thrown
        });
        
        // Method is not expected to return due to exception thrown
    }
}
