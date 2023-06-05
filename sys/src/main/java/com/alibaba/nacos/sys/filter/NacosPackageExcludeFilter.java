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

package com.alibaba.nacos.sys.filter;

import java.util.Set;

/**
 * Nacos server module execute filter.
 *
 * @author xiweng.yy
 */
public interface NacosPackageExcludeFilter {
    
    /**
     * Get the responsible module package prefix of filter.
     *
     * @return package prefix
     */
    String getResponsiblePackagePrefix();
    
    /**
     * According the class name and annotations to judge whether the class should be excluded by spring bean.
     *
     * @param className       name of this class
     * @param annotationNames annotations of this class
     * @return {@code true} if should be excluded, otherwise {@code false}
     */
    boolean isExcluded(String className, Set<String> annotationNames);
}
