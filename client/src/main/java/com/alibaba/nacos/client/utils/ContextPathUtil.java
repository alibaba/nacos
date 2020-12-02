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

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Context path Util.
 *
 * @author Wei.Wang
 */
public class ContextPathUtil {
    
    private static final String ROOT_WEB_CONTEXT_PATH = "/";
    
    /**
     * normalize context path.
     *
     * @param contextPath origin context path
     * @return normalized context path
     */
    public static String normalizeContextPath(String contextPath) {
        if (StringUtils.isBlank(contextPath) || ROOT_WEB_CONTEXT_PATH.equals(contextPath)) {
            return StringUtils.EMPTY;
        }
        return contextPath.startsWith("/") ? contextPath : "/" + contextPath;
    }
}
