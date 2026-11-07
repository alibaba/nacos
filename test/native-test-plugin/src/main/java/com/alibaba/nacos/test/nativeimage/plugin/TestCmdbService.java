/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.test.nativeimage.plugin;

import com.alibaba.nacos.api.cmdb.pojo.Entity;
import com.alibaba.nacos.api.cmdb.pojo.EntityEvent;
import com.alibaba.nacos.api.cmdb.pojo.Label;
import com.alibaba.nacos.api.cmdb.spi.CmdbService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal CMDB for the native tests: every IP has zone z1 so a label selector can match.
 */
public class TestCmdbService implements CmdbService {

    private static Entity entity(String ip) {
        Entity entity = new Entity();
        entity.setName(ip);
        entity.setType("ip");
        entity.setLabels(Map.of("zone", "z1"));
        return entity;
    }

    @Override
    public Set<String> getLabelNames() {
        return Set.of("zone");
    }

    @Override
    public Set<String> getEntityTypes() {
        return Set.of("ip");
    }

    @Override
    public Label getLabel(String labelName) {
        return null;
    }

    @Override
    @Deprecated
    public String getLabelValue(String entityName, String entityType, String labelName) {
        return "zone".equals(labelName) ? "z1" : null;
    }

    @Override
    @Deprecated
    public Map<String, String> getLabelValues(String entityName, String entityType) {
        return Map.of("zone", "z1");
    }

    @Override
    public Map<String, Map<String, Entity>> getAllEntities() {
        Map<String, Entity> ips = new HashMap<>();
        ips.put("127.0.0.1", entity("127.0.0.1"));
        return Map.of("ip", Collections.unmodifiableMap(ips));
    }

    @Override
    public List<EntityEvent> getEntityEvents(long timestamp) {
        return List.of();
    }

    @Override
    public Entity getEntity(String entityName, String entityType) {
        return entity(entityName);
    }
}
