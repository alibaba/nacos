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
import com.alibaba.nacos.naming.core.DomainsManager;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.web.BaseServlet;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

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
}
