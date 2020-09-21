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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.OverrideParameterRequestWrapper;
import com.alibaba.nacos.core.utils.ReuseHttpServletRequest;

/**
 * Distro IP and port tag generator.
 *
 * @author xiweng.yy
 */
public class DistroIpPortTagGenerator implements DistroTagGenerator {
    
    private static final String PARAMETER_IP = "ip";
    
    private static final String PARAMETER_PORT = "port";
    
    @Override
    public String getResponsibleTag(ReuseHttpServletRequest request) {
        String ip = request.getParameter(PARAMETER_IP);
        String port = request.getParameter(PARAMETER_PORT);
        if (StringUtils.isNotBlank(ip)) {
            ip = ip.trim();
        }
        port = StringUtils.isBlank(port) ? "0" : port.trim();
        return ip + ":" + port;
    }
    
    @Override
    public OverrideParameterRequestWrapper wrapperRequestWithTag(ReuseHttpServletRequest request, String tag) {
        return OverrideParameterRequestWrapper.buildRequest(request);
    }
}
