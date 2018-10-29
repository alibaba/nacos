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

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.alibaba.fastjson.JSONObject;
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

import java.util.Map;

/**
 * @author dungu.zpf
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/service")
public class ServiceController {

    @Autowired
    protected DomainsManager domainsManager;

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

    @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
    public String removeService(HttpServletRequest request) throws Exception {

        String serviceName = BaseServlet.required(request, "serviceName");
        if (domainsManager.getDomain(serviceName) == null) {
            throw new IllegalStateException("service doesn't exists.");
        }
        domainsManager.easyRemoveDom(serviceName);
        return "ok";
    }

}
