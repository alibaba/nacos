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
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.healthcheck.HealthCheckType;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author nkorange
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/cluster")
public class ClusterController {

    @Autowired
    protected ServiceManager serviceManager;

    @PutMapping
    public String update( @RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
                          @RequestParam String clusterName,
                          @RequestParam String serviceName,
                          @RequestParam String healthChecker,
                          @RequestParam(defaultValue = StringUtils.EMPTY) String metadata,
                          @RequestParam String checkPort,
                          @RequestParam String useInstancePort4Check) throws Exception {

        Service service = serviceManager.getService(namespaceId, serviceName);
        if (service == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "service not found:" + serviceName);
        }

        Cluster cluster = service.getClusterMap().get(clusterName);
        if (cluster == null) {
            Loggers.SRV_LOG.warn("[UPDATE-CLUSTER] cluster not exist, will create it: {}, service: {}", clusterName, serviceName);
            cluster = new Cluster(clusterName, service);
        }

        cluster.setDefCkport(NumberUtils.toInt(checkPort));
        cluster.setUseIPPort4Check(BooleanUtils.toBoolean(useInstancePort4Check));

        JSONObject healthCheckObj = JSON.parseObject(healthChecker);
        AbstractHealthChecker abstractHealthChecker;
        String type = healthCheckObj.getString("type");
        Class<AbstractHealthChecker> healthCheckClass = HealthCheckType.ofHealthCheckerClass(type);
        if(healthCheckClass == null){
            throw new NacosException(NacosException.INVALID_PARAM, "unknown health check type:" + healthChecker);
        }
        abstractHealthChecker = JSON.parseObject(healthChecker, healthCheckClass);

        cluster.setHealthChecker(abstractHealthChecker);
        cluster.setMetadata(UtilsAndCommons.parseMetadata(metadata));
        cluster.init();
        service.getClusterMap().put(clusterName, cluster);
        service.setLastModifiedMillis(System.currentTimeMillis());
        service.recalculateChecksum();
        service.validate();

        serviceManager.addOrReplaceService(service);

        return "ok";
    }

}
