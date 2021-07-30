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

package com.alibaba.nacos.common.utils;

/**
 * namespace(tenant) util.
 * Because config and naming treat namespace(tenant) differently,
 * this tool class can only be used by the config module.
 * @author klw(213539@qq.com)
 * @date 2020/10/12 17:56
 */
public class NamespaceUtil {
    
    private static final String NAMESPACE_PUBLIC_KEY = "public";
    
    private static final String NAMESPACE_NULL_KEY = "null";
    
    /**
     * Treat the namespace(tenant) parameters with values of "public" and "null" as an empty string.
     * @param tenant namespace(tenant) id
     * @return java.lang.String A namespace(tenant) string processed
     */
    public static String processNamespaceParameter(String tenant) {
        if (StringUtils.isBlank(tenant) || NAMESPACE_PUBLIC_KEY.equalsIgnoreCase(tenant) || NAMESPACE_NULL_KEY
                .equalsIgnoreCase(tenant)) {
            return "";
        }
        return tenant.trim();
    }

}
