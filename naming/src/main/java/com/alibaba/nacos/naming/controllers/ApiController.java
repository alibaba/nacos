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
package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.ClientInfo;
import com.alibaba.nacos.naming.web.CanDistro;
import com.alibaba.nacos.core.utils.OverrideParameterRequestWrapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.util.VersionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * Old API entry
 *
 * @author nkorange
 */
@RestController
@Deprecated
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api")
public class ApiController extends InstanceController {

    @Autowired
    private DistroMapper distroMapper;

    @Autowired
    private ServiceManager serviceManager;

    @RequestMapping("/allDomNames")
    public ObjectNode allDomNames(HttpServletRequest request) throws Exception {

        boolean responsibleOnly = Boolean.parseBoolean(WebUtils.optional(request, "responsibleOnly", "false"));
        Map<String, Set<String>> domMap = serviceManager.getAllServiceNames();
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        // For old DNS-F client:
        String dnsfVersion = "1.0.1";
        String agent = WebUtils.getUserAgent(request);
        ClientInfo clientInfo = new ClientInfo(agent);
        if (clientInfo.type == ClientInfo.ClientType.DNS &&
            clientInfo.version.compareTo(VersionUtil.parseVersion(dnsfVersion)) <= 0) {

            List<String> doms = new ArrayList<String>();
            Set<String> domSet = null;

            if (domMap.containsKey(Constants.DEFAULT_NAMESPACE_ID)) {
                domSet = domMap.get(Constants.DEFAULT_NAMESPACE_ID);
            }

            if (CollectionUtils.isEmpty(domSet)) {
                result.put("doms", JacksonUtils.transferToJsonNode(new HashSet<>()));
                result.put("count", 0);
                return result;
            }

            for (String dom : domSet) {
                if (distroMapper.responsible(dom) || !responsibleOnly) {
                    doms.add(NamingUtils.getServiceName(dom));
                }
            }

            result.put("doms", JacksonUtils.transferToJsonNode(doms));
            result.put("count", doms.size());
            return result;
        }

        Map<String, Set<String>> doms = new HashMap<>(16);
        int count = 0;
        for (String namespaceId : domMap.keySet()) {
            doms.put(namespaceId, new HashSet<>());
            for (String dom : domMap.get(namespaceId)) {
                if (distroMapper.responsible(dom) || !responsibleOnly) {
                    doms.get(namespaceId).add(NamingUtils.getServiceName(dom));
                }
            }
            count += doms.get(namespaceId).size();
        }

        result.put("doms", JacksonUtils.transferToJsonNode(doms));
        result.put("count", count);

        return result;
    }

    @RequestMapping("/hello")
    @ResponseBody
    public String hello(HttpServletRequest request) throws Exception {
        return "ok";
    }

    @RequestMapping("/srvIPXT")
    @ResponseBody
    public ObjectNode srvIPXT(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            Constants.DEFAULT_NAMESPACE_ID);

        String dom = WebUtils.required(request, "dom");
        String agent = WebUtils.getUserAgent(request);
        String clusters = WebUtils.optional(request, "clusters", StringUtils.EMPTY);
        String clientIP = WebUtils.optional(request, "clientIP", StringUtils.EMPTY);
        Integer udpPort = Integer.parseInt(WebUtils.optional(request, "udpPort", "0"));
        String env = WebUtils.optional(request, "env", StringUtils.EMPTY);
        boolean isCheck = Boolean.parseBoolean(WebUtils.optional(request, "isCheck", "false"));

        String app = WebUtils.optional(request, "app", StringUtils.EMPTY);

        String tenant = WebUtils.optional(request, "tid", StringUtils.EMPTY);

        boolean healthyOnly = Boolean.parseBoolean(WebUtils.optional(request, "healthyOnly", "false"));

        return doSrvIPXT(namespaceId, NamingUtils.getGroupedName(dom, Constants.DEFAULT_GROUP),
            agent, clusters, clientIP, udpPort, env, isCheck, app, tenant, healthyOnly);
    }

    @CanDistro
    @RequestMapping("/clientBeat")
    public ObjectNode clientBeat(HttpServletRequest request) throws Exception {
        OverrideParameterRequestWrapper requestWrapper = OverrideParameterRequestWrapper.buildRequest(request);
        requestWrapper.addParameter(CommonParams.SERVICE_NAME,
            Constants.DEFAULT_GROUP + Constants.SERVICE_INFO_SPLITER + WebUtils.required(request, "dom"));
        return beat(requestWrapper);
    }
}
