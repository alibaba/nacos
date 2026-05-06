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

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.ReuseHttpServletRequest;
import com.alibaba.nacos.naming.healthcheck.RsInfo;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Distro IP and port tag generator.
 *
 * @author xiweng.yy
 */
public class DistroIpPortTagGenerator implements DistroTagGenerator {
    
    private static final String PARAMETER_BEAT = "beat";
    
    private static final String PARAMETER_IP = "ip";
    
    private static final String PARAMETER_PORT = "port";
    
    @Override
    public String getResponsibleTag(ReuseHttpServletRequest request) {
        String ip = request.getParameter(PARAMETER_IP);
        String port = request.getParameter(PARAMETER_PORT);
        Map<String, String> bodyParameters = null;
        if (StringUtils.isBlank(ip) || StringUtils.isBlank(port)) {
            bodyParameters = parseBodyParameters(request);
            ip = StringUtils.isBlank(ip) ? bodyParameters.get(PARAMETER_IP) : ip;
            port = StringUtils.isBlank(port) ? bodyParameters.get(PARAMETER_PORT) : port;
        }
        if (StringUtils.isBlank(ip)) {
            // some old version clients using beat parameter
            String beatStr = request.getParameter(PARAMETER_BEAT);
            if (StringUtils.isBlank(beatStr) && null != bodyParameters) {
                beatStr = bodyParameters.get(PARAMETER_BEAT);
            }
            if (StringUtils.isNotBlank(beatStr)) {
                try {
                    RsInfo rsInfo = JacksonUtils.toObj(beatStr, RsInfo.class);
                    ip = rsInfo.getIp();
                    port = String.valueOf(rsInfo.getPort());
                } catch (NacosDeserializationException ignored) {
                }
            }
        }
        if (StringUtils.isNotBlank(ip)) {
            ip = ip.trim();
        }
        port = StringUtils.isBlank(port) ? "0" : port.trim();
        return ip + InternetAddressUtil.IP_PORT_SPLITER + port;
    }
    
    private Map<String, String> parseBodyParameters(ReuseHttpServletRequest request) {
        Map<String, String> result = new HashMap<>(4);
        try {
            Object body = request.getBody();
            if (!(body instanceof String)) {
                return result;
            }
            String bodyString = (String) body;
            if (StringUtils.isBlank(bodyString)) {
                return result;
            }
            for (String each : bodyString.split("&")) {
                int index = each.indexOf('=');
                if (index <= 0) {
                    continue;
                }
                String key = URLDecoder.decode(each.substring(0, index), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(each.substring(index + 1), StandardCharsets.UTF_8);
                result.put(key, value);
            }
        } catch (Exception ignored) {
        }
        return result;
    }
}
