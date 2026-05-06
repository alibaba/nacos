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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SafeBcryptPasswordEncoderTest.
 *
 * @author linuwmingshi
 */
public class SafeBcryptPasswordEncoderTest {
    
    /**
     * SafeBCryptPasswordEncoder.
     */
    private static final SafeBcryptPasswordEncoder ENCODER = new SafeBcryptPasswordEncoder();
    
    @Test
    void testValidPasswordLength() {
        String rawPassword =  StringUtils.repeat("A", AuthConstants.MAX_PASSWORD_LENGTH);
        String encodedPassword = ENCODER.encode(rawPassword);
        
        assertTrue(ENCODER.matches(rawPassword, encodedPassword), "72-character rawPassword should match");
    }
    
    @Test
    void testExcessivePasswordLength() {
        String rawPassword = StringUtils.repeat("A", AuthConstants.MAX_PASSWORD_LENGTH + 1);
        String encodedPassword = ENCODER.encode(rawPassword.substring(0, AuthConstants.MAX_PASSWORD_LENGTH));
        
        assertFalse(ENCODER.matches(rawPassword, encodedPassword), "73-character rawPassword should be rejected");
    }
    
    @Test
    void testEdgeCase() {
        String rawPassword72 = StringUtils.repeat("A", AuthConstants.MAX_PASSWORD_LENGTH);
        String rawPassword73 = rawPassword72 + "A";
        String encodedPassword = ENCODER.encode(rawPassword72);
        
        assertTrue(ENCODER.matches(rawPassword72, encodedPassword), "72-character password must pass");
        assertFalse(ENCODER.matches(rawPassword73, encodedPassword), "73-character password must fail");
    }
}
