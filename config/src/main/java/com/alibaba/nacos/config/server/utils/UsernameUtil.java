/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.common.utils.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Username util.
 *
 * @author WangRongguang
 */
public class UsernameUtil {

    private static final String PRESET_USERNAME_KEY = "Username";

    /**
     * Preset the username.
     *
     * @param response which response you want to preset the username.
     * @param username which username you want to preset.
     */
    public static void putUsernameInRequest(HttpServletResponse response, String username) {

        if (StringUtils.isBlank(username)) {
            return;
        }

        Cookie cookie = new Cookie(PRESET_USERNAME_KEY, username);
        cookie.setPath("/");
        response.addCookie(cookie);

    }

    /**
     * Get the preset username from the request.
     *
     * @param request which request you want to get the username.
     * @return username
     */
    public static String getUsernameFromRequest(HttpServletRequest request) {

        String username = null;
        Cookie[] cookies = request.getCookies();

        if (null == cookies) {
            return username;
        }

        for (Cookie cookie : cookies) {
            if (PRESET_USERNAME_KEY.equals(cookie.getName())) {
                username = cookie.getValue();
                break;
            }
        }
        return username;
    }
}
