/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.utils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RandomPasswordGenerator .
 *
 * @author : huangtianhui
 */
public class PasswordGeneratorUtil {
    
    private static final String LOWER_CASE = "abcdefghijklmnopqrstuvwxyz";
    
    private static final String UPPER_CASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    
    private static final String DIGITS = "0123456789";
    
    private static final String SPECIAL_CHARS = "!@#$%&";
    
    private static final int PASSWORD_LENGTH = 8;
    
    /**
     * generateRandomPassword.
     * @return
     */
    public static String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        
        List<Character> pwdChars = new ArrayList<>();
        
        pwdChars.add(LOWER_CASE.charAt(random.nextInt(LOWER_CASE.length())));
        pwdChars.add(UPPER_CASE.charAt(random.nextInt(UPPER_CASE.length())));
        pwdChars.add(DIGITS.charAt(random.nextInt(DIGITS.length())));
        pwdChars.add(SPECIAL_CHARS.charAt(random.nextInt(SPECIAL_CHARS.length())));
        
        // Fill the rest of the password with random characters from all categories
        String allCharacters = LOWER_CASE + UPPER_CASE + DIGITS + SPECIAL_CHARS;
        while (pwdChars.size() < PASSWORD_LENGTH) {
            pwdChars.add(allCharacters.charAt(random.nextInt(allCharacters.length())));
        }
        
        // Shuffle to avoid predictable order
        Collections.shuffle(pwdChars, random);
        
        // Build the final password string
        return pwdChars.stream().map(String::valueOf).collect(Collectors.joining());
    }
}
