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
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckerFactory;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.ClusterOperator;
import com.alibaba.nacos.naming.core.ClusterOperatorV1Impl;
import com.alibaba.nacos.naming.core.ClusterOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.web.NamingResourceParser;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Cluster controller.
 *
 * @author nkorange
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_CLUSTER_CONTEXT)
public class ClusterController {
    
    private final UpgradeJudgement upgradeJudgement;
    
    private final ClusterOperatorV1Impl clusterOperatorV1;
    
    private final ClusterOperatorV2Impl clusterOperatorV2;
    
    public ClusterController(UpgradeJudgement upgradeJudgement, ClusterOperatorV1Impl clusterOperatorV1,
            ClusterOperatorV2Impl clusterOperatorV2) {
        this.upgradeJudgement = upgradeJudgement;
        this.clusterOperatorV1 = clusterOperatorV1;
        this.clusterOperatorV2 = clusterOperatorV2;
    }
    
    /**
     * Update cluster.
     *
     * @param request http request
     * @return 'ok' if success
     * @throws Exception if failed
     */
    @PutMapping
    @Secured(action = ActionTypes.WRITE, parser = NamingResourceParser.class)
    public String update(HttpServletRequest request) throws Exception {
        final String namespaceId = WebUtils
                .optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        final String clusterName = WebUtils.required(request, CommonParams.CLUSTER_NAME);
        final String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        ClusterMetadata clusterMetadata = new ClusterMetadata();
        clusterMetadata.setHealthyCheckPort(NumberUtils.toInt(WebUtils.required(request, "checkPort")));
        clusterMetadata.setUseInstancePortForCheck(
                BooleanUtils.toBoolean(WebUtils.required(request, "useInstancePort4Check")));
        AbstractHealthChecker healthChecker = HealthCheckerFactory
                .deserialize(WebUtils.required(request, "healthChecker"));
        clusterMetadata.setHealthChecker(healthChecker);
        clusterMetadata.setHealthyCheckType(healthChecker.getType());
        clusterMetadata.setExtendData(
                UtilsAndCommons.parseMetadata(WebUtils.optional(request, "metadata", StringUtils.EMPTY)));
        judgeClusterOperator().updateClusterMetadata(namespaceId, serviceName, clusterName, clusterMetadata);
        return "ok";
    }
    
    private ClusterOperator judgeClusterOperator() {
        return upgradeJudgement.isUseGrpcFeatures() ? clusterOperatorV2 : clusterOperatorV1;
    }
}
