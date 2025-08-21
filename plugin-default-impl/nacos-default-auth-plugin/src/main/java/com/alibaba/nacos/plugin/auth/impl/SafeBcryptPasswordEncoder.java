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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt encoder that fixes the password length vulnerability.
 *
 * <p>Problem solved: When password length exceeds 72 characters, the original {@link BCryptPasswordEncoder}
 * only matches the first 72 characters, which could lead to different passwords being
 * validated as matching (e.g., passwords {@code "A".repeat(73)} and {@code "A".repeat(80)}
 * would be considered identical).
 *
 * <p>Fix logic: Adds length validation in {@link #matches(CharSequence, String)},
 * returning false directly if the password length exceeds 72.
 *
 * <p><strong>Recommendation:</strong> It is advised to add password length validation
 * during user registration/password modification to prevent login failures caused
 * by historical data issues.
 *
 * @see <a href="https://github.com/advisories/GHSA-mg83-c7gq-rv5c">Spring Security Password Length Vulnerability Advisory</a>
 * @author linwumignshi
 */
public class SafeBcryptPasswordEncoder extends BCryptPasswordEncoder {

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        // Reject excessively long passwords immediately
        if (rawPassword != null && rawPassword.length() > AuthConstants.MAX_PASSWORD_LENGTH) {
            return false;
        }
        return super.matches(rawPassword, encodedPassword);
    }
}