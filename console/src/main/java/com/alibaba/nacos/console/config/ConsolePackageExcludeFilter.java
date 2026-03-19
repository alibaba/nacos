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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.console.NacosConsole;
import com.alibaba.nacos.sys.filter.NacosPackageExcludeFilter;

import java.util.Set;

/**
 * Console module package exclude filter. Only Basic and Web contexts use NacosTypeExcludeFilter with
 * basePackages = "com.alibaba.nacos"; Console context uses default scan (console package only) and does not
 * apply this filter. So when this filter runs, it is always Basic/Web context and console package should be excluded.
 *
 * @author xiweng.yy
 */
public class ConsolePackageExcludeFilter implements NacosPackageExcludeFilter {
    
    @Override
    public String getResponsiblePackagePrefix() {
        return NacosConsole.class.getPackage().getName();
    }
    
    @Override
    public boolean isExcluded(String className, Set<String> annotationNames) {
        return true;
    }
}
