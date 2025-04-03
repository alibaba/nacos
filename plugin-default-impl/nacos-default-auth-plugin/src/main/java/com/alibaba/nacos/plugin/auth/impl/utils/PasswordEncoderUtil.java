/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.plugin.auth.impl.SafeBcryptPasswordEncoder;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;

/**
 * Password encoder tool.
 *
 * @author nacos
 */
public class PasswordEncoderUtil {
    
    public static Boolean matches(String raw, String encoded) {
        return new SafeBcryptPasswordEncoder().matches(raw, encoded);
    }
    
    /**
     * Encode password.
     *
     * @param raw password
     * @return encoded password
     */
    public static String encode(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        if (raw.length() > AuthConstants.MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password length must not exceed " + AuthConstants.MAX_PASSWORD_LENGTH + " characters");
        }
        return new SafeBcryptPasswordEncoder().encode(raw);
    }
}
