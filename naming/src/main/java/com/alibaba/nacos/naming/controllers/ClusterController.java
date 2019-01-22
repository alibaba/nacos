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
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.pojo.AbstractHealthChecker;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.DomainsManager;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/cluster")
public class ClusterController {

    @Autowired
    protected DomainsManager domainsManager;

    @RequestMapping(value = "", method = RequestMethod.PUT)
    public String update(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String clusterName = WebUtils.required(request, "clusterName");
        String serviceName = WebUtils.required(request, "serviceName");
        String healthChecker = WebUtils.required(request, "healthChecker");
        String metadata = WebUtils.optional(request, "metadata", StringUtils.EMPTY);
        String checkPort = WebUtils.required(request, "checkPort");
        String useInstancePort4Check = WebUtils.required(request, "useInstancePort4Check");

        VirtualClusterDomain domain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, serviceName);
        if (domain == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "service not found:" + serviceName);
        }

        Cluster cluster = domain.getClusterMap().get(clusterName);
        if (cluster == null) {
            Loggers.SRV_LOG.warn("[UPDATE-CLUSTER] cluster not exist, will create it: {}, service: {}", clusterName, serviceName);
            cluster = new Cluster();
            cluster.setName(clusterName);
        }

        cluster.setDefCkport(NumberUtils.toInt(checkPort));
        cluster.setUseIPPort4Check(BooleanUtils.toBoolean(useInstancePort4Check));

        JSONObject healthCheckObj = JSON.parseObject(healthChecker);
        AbstractHealthChecker abstractHealthChecker;

        switch (healthCheckObj.getString("type")) {
            case AbstractHealthChecker.Tcp.TYPE:
                abstractHealthChecker = JSON.parseObject(healthChecker, AbstractHealthChecker.Tcp.class);
                break;
            case AbstractHealthChecker.Http.TYPE:
                abstractHealthChecker = JSON.parseObject(healthChecker, AbstractHealthChecker.Http.class);
                break;
            case AbstractHealthChecker.Mysql.TYPE:
                abstractHealthChecker = JSON.parseObject(healthChecker, AbstractHealthChecker.Mysql.class);
                break;
            default:
                throw new NacosException(NacosException.INVALID_PARAM, "unknown health check type:" + healthChecker);
        }

        cluster.setHealthChecker(abstractHealthChecker);
        cluster.setMetadata(UtilsAndCommons.parseMetadata(metadata));

        domain.getClusterMap().put(clusterName, cluster);

        domain.setLastModifiedMillis(System.currentTimeMillis());
        domain.recalculateChecksum();
        domain.valid();

        domainsManager.easyAddOrReplaceDom(domain);

        return "ok";
    }
}
