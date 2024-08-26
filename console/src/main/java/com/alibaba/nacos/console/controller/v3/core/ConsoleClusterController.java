/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.controller.v3.core;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckerFactory;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.NumberUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.console.proxy.core.ClusterProxy;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

/**
 * .
 *
 * @author zhangyukun on:2024/8/16
 */
@RestController
@RequestMapping("/v3/console/core/cluster")
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class ConsoleClusterController {
    
    private final ClusterProxy clusterProxy;
    
    /**
     * Constructs a new ConsoleClusterController with the provided ClusterProxy.
     *
     * @param clusterProxy the proxy used for handling cluster-related operations
     */
    public ConsoleClusterController(ClusterProxy clusterProxy) {
        this.clusterProxy = clusterProxy;
    }
    
    /**
     * The console displays the list of cluster members.
     *
     * @param ipKeyWord search keyWord
     * @return all members
     */
    @GetMapping(value = "/nodes")
    @Secured(resource = Commons.NACOS_CORE_CONTEXT + "/cluster", action = ActionTypes.READ, signType = SignType.CONSOLE)
    public Result<Collection<Member>> getNodeList(@RequestParam(value = "keyword", required = false) String ipKeyWord) {
        Collection<Member> result = clusterProxy.getNodeList(ipKeyWord);
        return Result.success(result);
    }
    
    
    /**
     * Update cluster.
     *
     * @param request http request
     * @return 'ok' if success
     * @throws Exception if failed
     */
    @PutMapping
    @Secured(action = ActionTypes.WRITE)
    public Result<String> updateCluster(HttpServletRequest request) throws Exception {
        final String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
                Constants.DEFAULT_NAMESPACE_ID);
        final String clusterName = WebUtils.required(request, CommonParams.CLUSTER_NAME);
        final String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        
        ClusterMetadata clusterMetadata = new ClusterMetadata();
        clusterMetadata.setHealthyCheckPort(NumberUtils.toInt(WebUtils.required(request, "checkPort")));
        clusterMetadata.setUseInstancePortForCheck(
                ConvertUtils.toBoolean(WebUtils.required(request, "useInstancePort4Check")));
        AbstractHealthChecker healthChecker = HealthCheckerFactory.deserialize(
                WebUtils.required(request, "healthChecker"));
        clusterMetadata.setHealthChecker(healthChecker);
        clusterMetadata.setHealthyCheckType(healthChecker.getType());
        clusterMetadata.setExtendData(
                UtilsAndCommons.parseMetadata(WebUtils.optional(request, "metadata", StringUtils.EMPTY)));
        
        clusterProxy.updateClusterMetadata(namespaceId, serviceName, clusterName, clusterMetadata);
        return Result.success("ok");
    }
}
