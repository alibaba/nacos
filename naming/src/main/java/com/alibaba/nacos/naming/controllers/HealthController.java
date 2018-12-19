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
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.web.ApiCommands;
import com.alibaba.nacos.naming.web.OverrideParameterRequestWrapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 * @since 0.8.0
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_HEALTH_CONTEXT)
public class HealthController extends ApiCommands {

    @RequestMapping(value = "", method = RequestMethod.POST)
    public JSONObject update(HttpServletRequest request) throws Exception {
        return clientBeat(OverrideParameterRequestWrapper.buildRequest(request, "dom", WebUtils.required(request, "serviceName")));
    }
}
