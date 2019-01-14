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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.DomainsManager;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.healthcheck.HealthCheckMode;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.raft.RaftCore;
import com.alibaba.nacos.naming.selector.LabelSelector;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.naming.selector.Selector;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/service")
public class ServiceController {

    @Autowired
    protected DomainsManager domainsManager;

    @RequestMapping(value = "", method = RequestMethod.PUT)
    public String create(HttpServletRequest request) throws Exception {
        String serviceName = WebUtils.required(request, "serviceName");

        if (domainsManager.getDomain(serviceName) != null) {
            throw new IllegalArgumentException("specified service already exists, serviceName : " + serviceName);
        }

        float protectThreshold = NumberUtils.toFloat(WebUtils.optional(request, "protectThreshold", "0"));
        String healthCheckMode = WebUtils.optional(request, "healthCheckMode", "client");
        String metadata = WebUtils.optional(request, "metadata", StringUtils.EMPTY);
        String selector = WebUtils.optional(request, "selector", StringUtils.EMPTY);
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
        domObj.setSelector(parseSelector(selector));

        // now valid the dom. if failed, exception will be thrown
        domObj.setLastModifiedMillis(System.currentTimeMillis());
        domObj.recalculateChecksum();
        domObj.valid();

        domainsManager.easyAddOrReplaceDom(domObj);

        return "ok";
    }

    @RequestMapping(value = "", method = RequestMethod.DELETE)
    public String remove(HttpServletRequest request) throws Exception {

        String serviceName = WebUtils.required(request, "serviceName");

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

    @RequestMapping(value = "", method = RequestMethod.GET)
    public JSONObject detail(HttpServletRequest request) throws Exception {

        String serviceName = WebUtils.required(request, "serviceName");
        VirtualClusterDomain domain = (VirtualClusterDomain) domainsManager.getDomain(serviceName);
        if (domain == null) {
            throw new NacosException(NacosException.NOT_FOUND, "serivce " + serviceName + " is not found!");
        }

        JSONObject res = new JSONObject();
        res.put("name", serviceName);
        res.put("protectThreshold", domain.getProtectThreshold());

        res.put("healthCheckMode", HealthCheckMode.none.name());

        if (domain.getEnableHealthCheck()) {
            res.put("healthCheckMode", HealthCheckMode.server.name());
        }

        if (domain.getEnableClientBeat()) {
            res.put("healthCheckMode", HealthCheckMode.client.name());
        }

        res.put("metadata", domain.getMetadata());

        res.put("selector", domain.getSelector());

        res.put("instanceTimestamp", RaftCore.getDatum(UtilsAndCommons.getIPListStoreKey(domain)).timestamp.get());
        res.put("serviceTimestamp", RaftCore.getDatum(UtilsAndCommons.getDomStoreKey(domain)).timestamp.get());

        return res;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public JSONObject list(HttpServletRequest request) throws Exception {

        int pageNo = NumberUtils.toInt(WebUtils.required(request, "pageNo"));
        int pageSize = NumberUtils.toInt(WebUtils.required(request, "pageSize"));
        String selectorString = WebUtils.optional(request, "selector", StringUtils.EMPTY);

        List<String> doms = domainsManager.getAllDomNamesList();

        if (StringUtils.isNotBlank(selectorString)) {

            JSONObject selectorJson = JSON.parseObject(selectorString);
            switch (SelectorType.valueOf(selectorJson.getString("type"))) {
                case label:
                    String expression = selectorJson.getString("expression");
                    if (StringUtils.isBlank(expression)) {
                        break;
                    }
                    expression = StringUtils.deleteWhitespace(expression);
                    // Now we only support the following expression:
                    // INSTANCE.metadata.xxx = 'yyy' or
                    // SERVICE.metadata.xxx = 'yyy'
                    String[] terms = expression.split("=");
                    String[] factors = terms[0].split("\\.");
                    switch (factors[0]) {
                        case "INSTANCE":
                            doms = filterInstanceMetadata(doms, factors[factors.length - 1], terms[1].replace("'", ""));
                            break;
                        case "SERVICE":
                            doms = filterServiceMetadata(doms, factors[factors.length - 1], terms[1].replace("'", ""));
                            break;
                        default:
                            break;
                    }
                    break;
                default:
                    break;
            }
        }

        int start = (pageNo - 1) * pageSize;
        int end = start + pageSize;

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

    @RequestMapping(value = "", method = RequestMethod.POST)
    public String update(HttpServletRequest request) throws Exception {

        String serviceName = WebUtils.required(request, "serviceName");
        float protectThreshold = NumberUtils.toFloat(WebUtils.required(request, "protectThreshold"));
        String healthCheckMode = WebUtils.required(request, "healthCheckMode");
        String metadata = WebUtils.optional(request, "metadata", StringUtils.EMPTY);
        String selector = WebUtils.optional(request, "selector", StringUtils.EMPTY);

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

        domain.setSelector(parseSelector(selector));

        domain.setLastModifiedMillis(System.currentTimeMillis());
        domain.recalculateChecksum();
        domain.valid();

        domainsManager.easyAddOrReplaceDom(domain);

        return "ok";
    }

    private List<String> filterInstanceMetadata(List<String> serivces, String key, String value) {

        List<String> filteredServices = new ArrayList<>();
        for (String service : serivces) {
            VirtualClusterDomain serviceObj = (VirtualClusterDomain) domainsManager.getDomain(service);
            if (serviceObj == null) {
                continue;
            }
            for (IpAddress address : serviceObj.allIPs()) {
                if (value.equals(address.getMetadata().get(key))) {
                    filteredServices.add(service);
                    break;
                }
            }
        }
        return filteredServices;
    }

    private List<String> filterServiceMetadata(List<String> serivces, String key, String value) {

        List<String> filteredServices = new ArrayList<>();
        for (String service : serivces) {
            VirtualClusterDomain serviceObj = (VirtualClusterDomain) domainsManager.getDomain(service);
            if (serviceObj == null) {
                continue;
            }
            if (value.equals(serviceObj.getMetadata().get(key))) {
                filteredServices.add(service);
            }

        }
        return filteredServices;
    }

    private Selector parseSelector(String selectorJsonString) throws NacosException {

        if (StringUtils.isBlank(selectorJsonString)) {
            return new NoneSelector();
        }

        JSONObject selectorJson = JSON.parseObject(selectorJsonString);
        switch (SelectorType.valueOf(selectorJson.getString("type"))) {
            case none:
                return new NoneSelector();
            case label:
                String expression = selectorJson.getString("expression");
                Set<String> labels = LabelSelector.parseExpression(expression);
                LabelSelector labelSelector = new LabelSelector();
                labelSelector.setExpression(expression);
                labelSelector.setLabels(labels);
                return labelSelector;
            default:
                throw new NacosException(NacosException.INVALID_PARAM, "not match any type of selector!");
        }
    }
}
