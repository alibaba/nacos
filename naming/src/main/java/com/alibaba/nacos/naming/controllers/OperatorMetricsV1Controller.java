/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.core.controller.compatibility.Compatibility;
import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal v1 compatibility for GET /v1/ns/operator/metrics (onlyStatus=true only). Loaded only when api-legacy-adapter
 * is not on classpath; otherwise legacy adapter's OperatorController serves full metrics. For full metrics use v3 API.
 *
 * @author xiweng.yy
 */
@RestController
@RequestMapping({
    UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_OPERATOR_CONTEXT,
    UtilsAndCommons.NACOS_NAMING_CONTEXT + "/ops"})
@ConditionalOnMissingClass("com.alibaba.nacos.legacy.adapter.naming.OperatorController")
public class OperatorMetricsV1Controller {
    
    public OperatorMetricsV1Controller() {
    }
    
    /**
     * Get metrics (only status). Kept for old clients; full metrics available at v3 API.
     */
    @Since("3.2.0")
    @GetMapping("/metrics")
    @Compatibility(apiType = ApiType.OPEN_API,
        alternatives = "GET ${contextPath:nacos}/v3/admin/ns/ops/metrics")
    public Map<String, String> metrics() {
        Map<String, String> result = new HashMap<>();
        result.put("status", ServerStatus.UP.name());
        return result;
    }
}
