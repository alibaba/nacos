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

package com.alibaba.nacos.core.utils;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ReuseHttpRequest.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface ReuseHttpRequest extends HttpServletRequest {
    
    /**
     * get request body.
     *
     * @return object
     * @throws Exception exception
     */
    Object getBody() throws Exception;
    
    /**
     * Remove duplicate values from the array.
     *
     * @param request {@link HttpServletRequest}
     * @return {@link Map}
     */
    default Map<String, String[]> toDuplication(HttpServletRequest request) {
        Map<String, String[]> tmp = request.getParameterMap();
        Map<String, String[]> result = new HashMap<>(tmp.size());
        Set<String> set = new HashSet<>();
        for (Map.Entry<String, String[]> entry : tmp.entrySet()) {
            set.addAll(Arrays.asList(entry.getValue()));
            result.put(entry.getKey(), set.toArray(new String[0]));
            set.clear();
        }
        return result;
    }
}
