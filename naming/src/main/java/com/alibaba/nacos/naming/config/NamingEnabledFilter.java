/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.config;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.NamingApp;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.filter.NacosPackageExcludeFilter;

import java.util.Set;

import static com.alibaba.nacos.sys.env.EnvUtil.FUNCTION_MODE_NAMING;

/**
 * Naming module enabled filter by spring packages scan.
 *
 * @author xiweng.yy
 */
public class NamingEnabledFilter implements NacosPackageExcludeFilter {
    
    @Override
    public String getResponsiblePackagePrefix() {
        return NamingApp.class.getPackage().getName();
    }
    
    @Override
    public boolean isExcluded(String className, Set<String> annotationNames) {
        String functionMode = EnvUtil.getFunctionMode();
        // When not specified naming mode or specified all mode, the naming module not start and load.
        if (StringUtils.isEmpty(functionMode)) {
            return false;
        }
        return !FUNCTION_MODE_NAMING.equals(functionMode);
    }
}
