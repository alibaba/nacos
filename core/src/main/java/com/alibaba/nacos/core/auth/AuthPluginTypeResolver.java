/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * Resolves the startup auth plugin selection from the standard key and its legacy alias.
 *
 * @author Nacos
 */
public final class AuthPluginTypeResolver {
    
    private AuthPluginTypeResolver() {
    }
    
    /**
     * Resolve the selected auth plugin name. The standard key takes precedence.
     *
     * @return selected auth plugin name
     */
    public static String resolve() {
        String result = EnvUtil.getProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE);
        if (StringUtils.isNotBlank(result)) {
            return result.trim();
        }
        result = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE);
        return StringUtils.isBlank(result) ? StringUtils.EMPTY : result.trim();
    }
}
