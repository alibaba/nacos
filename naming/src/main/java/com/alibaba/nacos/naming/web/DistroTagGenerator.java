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

package com.alibaba.nacos.naming.web;

import com.alibaba.nacos.core.utils.OverrideParameterRequestWrapper;
import com.alibaba.nacos.core.utils.ReuseHttpServletRequest;

/**
 * Distro tag generator.
 *
 * @author xiweng.yy
 */
public interface DistroTagGenerator {
    
    /**
     * Get responsible tag from http request.
     *
     * @param request http request.
     * @return responsible tag for distro.
     */
    String getResponsibleTag(ReuseHttpServletRequest request);
    
    /**
     * Wrapper a new request with tag.
     *
     * @param request original request
     * @param tag     tag
     * @return request wrapper
     */
    OverrideParameterRequestWrapper wrapperRequestWithTag(ReuseHttpServletRequest request, String tag);
}
