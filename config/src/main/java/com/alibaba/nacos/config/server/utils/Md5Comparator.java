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

package com.alibaba.nacos.config.server.utils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * The interface Md5 comparator.
 *
 * @author Sunrisea
 */
public interface Md5Comparator {
    
    /**
     * Gets md 5 comparator name.
     *
     * @return the md 5 comparator name
     */
    public String getName();
    
    /**
     * Compare md 5 list.
     *
     * @param request      the request
     * @param response     the response
     * @param clientMd5Map the client md 5 map
     * @return the list
     */
    public List<String> compareMd5(HttpServletRequest request, HttpServletResponse response,
            Map<String, String> clientMd5Map);
}
