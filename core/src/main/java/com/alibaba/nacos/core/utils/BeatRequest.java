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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;

/**
 * beat request.
 *
 * @author zrlw@sina.com
 */
public class BeatRequest implements Serializable {
    
    private static final long serialVersionUID = 4949100576133626692L;
    
    private String id;
    
    private String ip;
    
    private int port;
    
    private String serviceName;
    
    private String namespaceId;
    
    private String clusterName;
    
    private String beat;
    
    /**
     * get beat request object from HttpServletRequest.
     * @param request HttpServletRequest
     * @return beat request
     */
    public static BeatRequest get(HttpServletRequest request, String groupedServiceName) {
        BeatRequest beatRequest = new BeatRequest();
        beatRequest.id = SimpleBeatInfo.getBeatId(request);
        beatRequest.beat = WebUtils.optional(request, "beat", StringUtils.EMPTY);
        beatRequest.clusterName = WebUtils.optional(request, CommonParams.CLUSTER_NAME, Constants.DEFAULT_CLUSTER_NAME);
        beatRequest.ip = WebUtils.optional(request, "ip", StringUtils.EMPTY);
        beatRequest.port = Integer.parseInt(WebUtils.optional(request, "port", "0"));
        beatRequest.namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        beatRequest.serviceName = groupedServiceName;
        return beatRequest;
    }
    
    public String getId() {
        return id;
    }
    
    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public String getClusterName() {
        return clusterName;
    }
    
    public String getBeat() {
        return beat;
    }
}
