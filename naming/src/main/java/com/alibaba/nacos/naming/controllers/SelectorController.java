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

import com.alibaba.nacos.common.util.WebUtils;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.selector.Selector;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.alibaba.nacos.naming.selector.SelectorType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/selector")
public class SelectorController {

    @Autowired
    private SelectorManager selectorManager;

    @RequestMapping(value = "", method = RequestMethod.PUT)
    public String add(HttpServletRequest request) throws Exception {
        String selectorName = WebUtils.required(request, "selectorName");

        String type = WebUtils.required(request, "type");

        SelectorType selectorType = SelectorType.valueOf(type);

        switch (selectorType) {
            case label:
                String labels = WebUtils.required(request, "labels");
                Set<String> labelSet = new HashSet<>();
                Collections.addAll(labelSet, labels.split(","));
                selectorManager.addLabelSelector(selectorName, labelSet);
                break;
            default:
                break;
        }

        return "ok";
    }

    @RequestMapping(value = "", method = RequestMethod.DELETE)
    public String remove(HttpServletRequest request) throws Exception {
        String selectorName = WebUtils.required(request, "selectorName");
        selectorManager.removeSelector(selectorName);
        return "ok";
    }

    @RequestMapping(value = "/detail", method = RequestMethod.GET)
    public Selector detail(HttpServletRequest request) throws Exception {
        return selectorManager.getSelector(WebUtils.required(request, "selectorName"));
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public Set<String> list(HttpServletRequest request) throws Exception {
        return selectorManager.getSelectorNames();
    }
}
