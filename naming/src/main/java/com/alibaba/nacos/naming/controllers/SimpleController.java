/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.consistency.ephemeral.simple.SimpleConsistencyServiceImpl;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * RESTful web services for "Yet another simple consistency service".
 *
 * @author lostcharlie
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/simple")
public class SimpleController {
    private Serializer serializer;
    private SimpleConsistencyServiceImpl consistencyService;

    public Serializer getSerializer() {
        return serializer;
    }

    @Autowired
    private void setSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    public SimpleConsistencyServiceImpl getConsistencyService() {
        return consistencyService;
    }

    @Autowired
    private void setConsistencyService(SimpleConsistencyServiceImpl consistencyService) {
        this.consistencyService = consistencyService;
    }

    public void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
        throw new NacosException(NacosException.SERVER_ERROR, "NotImplemented");
    }

    public void onHeartbeat(HttpServletRequest request, HttpServletResponse response) throws Exception {
        throw new NacosException(NacosException.SERVER_ERROR, "NotImplemented");
    }

    public void onAck(HttpServletRequest request, HttpServletResponse response) throws Exception {
        throw new NacosException(NacosException.SERVER_ERROR, "NotImplemented");
    }

    public void onUpdate(HttpServletRequest request, HttpServletResponse response) throws Exception {
        throw new NacosException(NacosException.SERVER_ERROR, "NotImplemented");
    }
}
