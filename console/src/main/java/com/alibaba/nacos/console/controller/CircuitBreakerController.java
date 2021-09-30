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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreaker;
import com.alibaba.nacos.core.remote.circuitbreaker.ConfigSerializer;
import com.alibaba.nacos.core.utils.Commons;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.micrometer.core.instrument.util.JsonUtils;
import jdk.nashorn.internal.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Circuit breaker controller to update/get config/status
 *
 * @author chuzefang
 */
@RestController
@RequestMapping("/v1/console/cb")
@Service
public class CircuitBreakerController {

    @PostConstruct
    private void init() {
        circuitBreaker.registerPoint("abc");
    }

    @Autowired
    CircuitBreaker circuitBreaker;

    @PostMapping(value = "/update")
    public RestResult<String> updateConfig(@RequestBody Map<String, Object> request) {

        String name = "strategyName";
        if (!request.containsKey(name)) {
            return RestResultUtils.failed("Strategy name not specified");
        }
        Object strategyName = request.get(name);

        ServiceLoader<ConfigSerializer> configSerializers = ServiceLoader.load(ConfigSerializer.class);

        System.out.println(JacksonUtils.toJson(request));

        try {
            // SPI mechanism to load rule implementation as current circuit breaker strategy
            for (ConfigSerializer configSerializer : configSerializers) {
                System.out.println(configSerializer.getName());
                if (strategyName.toString().equals(configSerializer.getName())) {
                    System.out.println("succccccc");
                    configSerializer.serializeConfig(request);
                }
            }
        } catch (Exception e) {
            return RestResultUtils.failed("Strategy serialization failed");
        }


        return RestResultUtils.success();
    }

    @GetMapping(value = "/test")
    public RestResult<String> testApplyForStrategy() {
        for (int i = 0; i <1000; i ++) {
            System.out.println(circuitBreaker.applyStrategy("abc", new ArrayList<>()));
        }
        return RestResultUtils.success();
    }

}
