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

package com.alibaba.nacos.core.controller;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.consistency.ap.APProtocol;
import com.alibaba.nacos.consistency.store.KVStore;
import com.alibaba.nacos.core.distributed.distro.DistroKVStore;
import com.alibaba.nacos.core.distributed.distro.DistroProtocol;
import com.alibaba.nacos.core.distributed.distro.KVManager;
import com.alibaba.nacos.core.distributed.distro.core.DistroServer;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SpringUtils;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@RequestMapping(Commons.NACOS_CORE_CONTEXT + "/distro")
@SuppressWarnings("all")
public class DistroController extends BaseController {

    private DistroProtocol protocol;

    private DistroServer distroServer;

    private Serializer serializer;

    private KVManager kvManager;

    @PostConstruct
    protected void init() {
        serializer = SerializeFactory.getDefault();
        protocol = (DistroProtocol) SpringUtils.getBean(APProtocol.class);
        distroServer = protocol.getDistroServer();
        kvManager = distroServer.getKvManager();
    }

    @PutMapping("/items")
    public ResponseEntity<String> onSyncDatum(@RequestBody ResResult<Map<String, Map<String, KVStore.Item>>> result) throws Exception {
        Map<String, Map<String, KVStore.Item>> dataMap = result.getData();
        if (dataMap.isEmpty()) {
            Loggers.DISTRO.error("[onSync] receive empty entity!");
            throw new NacosException(NacosException.INVALID_PARAM, "receive empty entity!");
        }

        dataMap.forEach((s, stringItemMap) -> Optional.ofNullable(kvManager.get(s))
                .ifPresent(store -> store.load(stringItemMap)));

        return ResponseEntity.ok("ok");

    }

    @PutMapping("/checksum")
    public ResponseEntity<String> syncChecksum(@RequestParam String source,
                                               @RequestBody Map<String, Map<String, String>> dataMap) {
        distroServer.onReceiveChecksums(dataMap, source);
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/items")
    public ResponseEntity<String> get(@RequestBody ResResult<Map<String, String>> result) throws Exception {
        Map<String, String> body = result.getData();
        String storeName = body.get("storeName");
        String keys = body.get("keys");
        String keySplitter = ",";
        Map<String, Map<String, KVStore.Item>> itemMap = new HashMap<>(8);

        DistroKVStore store = kvManager.get(storeName);

        Map<String, KVStore.Item> sub = new HashMap<>(64);

        if (store != null) {
            for (String key : keys.split(keySplitter)) {
                KVStore.Item datum = store.getItemByKey(key);
                if (datum == null) {
                    continue;
                }
                sub.put(key, datum);
            }

            itemMap.put(storeName, sub);
        }

        String content = new String(serializer.serialize(itemMap), StandardCharsets.UTF_8);
        return ResponseEntity.ok(content);
    }

    @GetMapping("/all/items")
    public ResponseEntity<String> getAllDatums() {

        Map<String, Map<String, KVStore.Item>> itemMap = new HashMap<>(8);

        kvManager.list().forEach((s, store) -> itemMap.putAll(store.getAll()));

        String content = new String(serializer.serialize(itemMap), StandardCharsets.UTF_8);

        return ResponseEntity.ok(content);
    }

}
