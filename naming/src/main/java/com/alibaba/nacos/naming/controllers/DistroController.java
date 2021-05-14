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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.DistroHttpData;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.combined.DistroHttpCombinedKey;
import com.alibaba.nacos.core.distributed.distro.DistroProtocol;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Restful methods for Partition protocol.
 *
 * @author nkorange
 * @since 1.0.0
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/distro")
public class DistroController {
    
    @Autowired
    private DistroProtocol distroProtocol;
    
    @Autowired
    private ServiceManager serviceManager;
    
    @Autowired
    private SwitchDomain switchDomain;
    
    /**
     * Synchronize datum.
     *
     * @param dataMap data map
     * @return 'ok' if success
     * @throws Exception if failed
     */
    @PutMapping("/datum")
    public ResponseEntity onSyncDatum(@RequestBody Map<String, Datum<Instances>> dataMap) throws Exception {
        
        if (dataMap.isEmpty()) {
            Loggers.DISTRO.error("[onSync] receive empty entity!");
            throw new NacosException(NacosException.INVALID_PARAM, "receive empty entity!");
        }
        
        for (Map.Entry<String, Datum<Instances>> entry : dataMap.entrySet()) {
            if (KeyBuilder.matchEphemeralInstanceListKey(entry.getKey())) {
                String namespaceId = KeyBuilder.getNamespace(entry.getKey());
                String serviceName = KeyBuilder.getServiceName(entry.getKey());
                if (!serviceManager.containService(namespaceId, serviceName) && switchDomain
                        .isDefaultInstanceEphemeral()) {
                    serviceManager.createEmptyService(namespaceId, serviceName, true);
                }
                DistroHttpData distroHttpData = new DistroHttpData(createDistroKey(entry.getKey()), entry.getValue());
                distroProtocol.onReceive(distroHttpData);
            }
        }
        return ResponseEntity.ok("ok");
    }
    
    /**
     * Checksum.
     *
     * @param source  source server
     * @param dataMap checksum map
     * @return 'ok'
     */
    @PutMapping("/checksum")
    public ResponseEntity syncChecksum(@RequestParam String source, @RequestBody Map<String, String> dataMap) {
        DistroHttpData distroHttpData = new DistroHttpData(createDistroKey(source), dataMap);
        distroProtocol.onVerify(distroHttpData, source);
        return ResponseEntity.ok("ok");
    }
    
    /**
     * Get datum.
     *
     * @param body keys of data
     * @return datum
     * @throws Exception if failed
     */
    @GetMapping("/datum")
    public ResponseEntity get(@RequestBody String body) throws Exception {
        
        JsonNode bodyNode = JacksonUtils.toObj(body);
        String keys = bodyNode.get("keys").asText();
        String keySplitter = ",";
        DistroHttpCombinedKey distroKey = new DistroHttpCombinedKey(KeyBuilder.INSTANCE_LIST_KEY_PREFIX, "");
        for (String key : keys.split(keySplitter)) {
            distroKey.getActualResourceTypes().add(key);
        }
        DistroData distroData = distroProtocol.onQuery(distroKey);
        return ResponseEntity.ok(distroData.getContent());
    }
    
    /**
     * Get all datums.
     *
     * @return all datums
     */
    @GetMapping("/datums")
    public ResponseEntity getAllDatums() {
        DistroData distroData = distroProtocol.onSnapshot(KeyBuilder.INSTANCE_LIST_KEY_PREFIX);
        return ResponseEntity.ok(distroData.getContent());
    }
    
    private DistroKey createDistroKey(String resourceKey) {
        return new DistroKey(resourceKey, KeyBuilder.INSTANCE_LIST_KEY_PREFIX);
    }
}
