/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

import java.util.Collections;
import java.util.List;

/**
 * Page Utils.
 *
 * @author xiweng.yy
 */
public class PageUtil {
    
    /**
     * Do page operation for input list.
     *
     * @param source    need paged source list
     * @param page      page number
     * @param pageSize  size of each page
     * @param <T>       The Type of List element.
     * @return Empty list if input list is empty or expected page is larger than source list size, otherwise paged list.
     */
    public static <T> List<T> subPage(List<T> source, int page, int pageSize) {
        if (source.isEmpty()) {
            return source;
        }
        int start = (page - 1) * pageSize;
        
        if (start < 0) {
            start = 0;
        }
        int end = start + pageSize;
        
        if (start > source.size()) {
            start = source.size();
        }
        
        if (end > source.size()) {
            end = source.size();
        }
        if (source.size() > start) {
            return source.subList(start, end);
        }
        return Collections.emptyList();
    }
}
