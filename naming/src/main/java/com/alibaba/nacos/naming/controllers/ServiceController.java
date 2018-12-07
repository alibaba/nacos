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

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.naming.core.DomainsManager;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.healthcheck.HealthCheckMode;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.web.BaseServlet;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/service")
public class ServiceController {

    @Autowired
    protected DomainsManager domainsManager;

    @RequestMapping(value = "/create", method = RequestMethod.PUT)
    public String create(HttpServletRequest request) throws Exception {
        String serviceName = BaseServlet.required(request, "serviceName");

        if (domainsManager.getDomain(serviceName) != null) {
            throw new IllegalArgumentException("specified service already exists, serviceName : " + serviceName);
        }

        float protectThreshold = NumberUtils.toFloat(BaseServlet.optional(request, "protectThreshold", "0"));
        String healthCheckMode = BaseServlet.optional(request, "healthCheckMode", "client");
        String metadata = BaseServlet.optional(request, "metadata", StringUtils.EMPTY);
        Map<String, String> metadataMap = new HashMap<>(16);
        if (StringUtils.isNotBlank(metadata)) {
            metadataMap = UtilsAndCommons.parseMetadata(metadata);
        }

        VirtualClusterDomain domObj = new VirtualClusterDomain();
        domObj.setName(serviceName);
        domObj.setProtectThreshold(protectThreshold);
        domObj.setEnableHealthCheck(HealthCheckMode.server.name().equals(healthCheckMode.toLowerCase()));
        domObj.setEnabled(true);
        domObj.setEnableClientBeat(HealthCheckMode.client.name().equals(healthCheckMode.toLowerCase()));
        domObj.setMetadata(metadataMap);

        // now valid the dom. if failed, exception will be thrown
        domObj.setLastModifiedMillis(System.currentTimeMillis());
        domObj.recalculateChecksum();
        domObj.valid();

        domainsManager.easyAddOrReplaceDom(domObj);

        return "ok";
    }

    @RequestMapping(value = "/remove", method = RequestMethod.DELETE)
    public String remove(HttpServletRequest request) throws Exception {

        String serviceName = BaseServlet.required(request, "serviceName");

        VirtualClusterDomain service = (VirtualClusterDomain) domainsManager.getDomain(serviceName);
        if (service == null) {
            throw new IllegalArgumentException("specified service not exist, serviceName : " + serviceName);
        }

        if (!service.allIPs().isEmpty()) {
            throw new IllegalArgumentException("specified service has instances, serviceName : " + serviceName);
        }

        domainsManager.easyRemoveDom(serviceName);

        return "ok";
    }

    @RequestMapping(value = "/detail")
    public Service detail(HttpServletRequest request) throws Exception {

        String serviceName = BaseServlet.required(request, "serviceName");
        VirtualClusterDomain domain = (VirtualClusterDomain) domainsManager.getDomain(serviceName);
        if (domain == null) {
            throw new NacosException(NacosException.NOT_FOUND, "serivce " + serviceName + " is not found!");
        }

        Service service = new Service(serviceName);
        service.setName(serviceName);
        service.setProtectThreshold(domain.getProtectThreshold());
        service.setHealthCheckMode(HealthCheckMode.none.name());
        if (domain.getEnableHealthCheck()) {
            service.setHealthCheckMode(HealthCheckMode.server.name());
        }
        if (domain.getEnableClientBeat()) {
            service.setHealthCheckMode(HealthCheckMode.client.name());
        }
        service.setMetadata(domain.getMetadata());

        return service;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public JSONObject list(HttpServletRequest request) throws Exception {

        int pageNo = NumberUtils.toInt(BaseServlet.required(request, "pageNo"));
        int pageSize = NumberUtils.toInt(BaseServlet.required(request, "pageSize"));

        int start = (pageNo - 1) * pageSize;
        int end = start + pageSize;

        List<String> doms = domainsManager.getAllDomNamesList();

        if (start < 0) {
            start = 0;
        }

        if (end > doms.size()) {
            end = doms.size();
        }

        JSONObject result = new JSONObject();

        result.put("doms", doms.subList(start, end));
        result.put("count", doms.size());

        return result;

    }


    @RequestMapping(value = "/update", method = RequestMethod.POST)
    public String update(HttpServletRequest request) throws Exception {

        String serviceName = BaseServlet.required(request, "serviceName");
        float protectThreshold = NumberUtils.toFloat(BaseServlet.required(request, "protectThreshold"));
        String healthCheckMode = BaseServlet.required(request, "healthCheckMode");
        String metadata = BaseServlet.optional(request, "metadata", StringUtils.EMPTY);

        VirtualClusterDomain domain = (VirtualClusterDomain) domainsManager.getDomain(serviceName);
        if (domain == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "service " + serviceName + " not found!");
        }

        domain.setProtectThreshold(protectThreshold);

        if (HealthCheckMode.server.name().equals(healthCheckMode)) {
            domain.setEnableHealthCheck(true);
            domain.setEnableClientBeat(false);
        }

        if (HealthCheckMode.client.name().equals(healthCheckMode)) {
            domain.setEnableClientBeat(true);
            domain.setEnableHealthCheck(false);
        }

        if (HealthCheckMode.none.name().equals(healthCheckMode)) {
            domain.setEnableClientBeat(false);
            domain.setEnableHealthCheck(false);
        }

        Map<String, String> metadataMap = UtilsAndCommons.parseMetadata(metadata);
        domain.setMetadata(metadataMap);

        domain.setLastModifiedMillis(System.currentTimeMillis());
        domain.recalculateChecksum();
        domain.valid();

        domainsManager.easyAddOrReplaceDom(domain);

        return "ok";
    }
}
