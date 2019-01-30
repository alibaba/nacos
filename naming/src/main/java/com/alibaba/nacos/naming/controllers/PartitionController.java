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

import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.util.IoUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.ephemeral.partition.PartitionConsistencyServiceImpl;
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Restful methods for Partition protocol.
 *
 * @author nkorange
 * @since 1.0.0
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/partition")
public class PartitionController {

    @Autowired
    private Serializer serializer;

    @Autowired
    private PartitionConsistencyServiceImpl consistencyService;

    @RequestMapping("/onSync")
    public String onSync(HttpServletRequest request, HttpServletResponse response) throws Exception {
        byte[] data = IoUtils.tryDecompress(request.getInputStream());

        String entity = IOUtils.toString(request.getInputStream(), "UTF-8");

        Map<String, Datum<Instances>> dataMap =
            serializer.deserializeMap(data, Instances.class);

        for (Map.Entry<String, Datum<Instances>> entry : dataMap.entrySet()) {
            if (KeyBuilder.matchEphemeralInstanceListKey(entry.getKey())) {
                consistencyService.onPut(entry.getKey(), entry.getValue().value);
            }
        }
        return "ok";
    }

    @RequestMapping("/syncTimestamps")
    public String syncTimestamps(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String source = WebUtils.required(request, "source");
        byte[] data = IoUtils.tryDecompress(request.getInputStream());
        Map<String, Long> dataMap = serializer.deserialize(data, new TypeReference<Map<String, Long>>(){});
        consistencyService.onReceiveTimestamps(dataMap, source);
        return "ok";
    }

    @RequestMapping("/get")
    public void get(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String keys = WebUtils.required(request, "keys");
        String keySplitter = ",";
        Map<String, Datum<?>> datumMap = new HashMap<>(64);
        for (String key : keys.split(keySplitter)) {
            datumMap.put(key, consistencyService.get(key));
        }
        response.getWriter().write(new String(serializer.serialize(datumMap), "UTF-8"));
    }
}
