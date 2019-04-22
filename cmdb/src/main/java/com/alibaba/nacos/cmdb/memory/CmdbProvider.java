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
package com.alibaba.nacos.cmdb.memory;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.cmdb.spi.CmdbService;
import com.alibaba.nacos.api.cmdb.pojo.Entity;
import com.alibaba.nacos.api.cmdb.pojo.EntityEvent;
import com.alibaba.nacos.api.cmdb.pojo.Label;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.cmdb.core.SwitchAndOptions;
import com.alibaba.nacos.cmdb.service.CmdbReader;
import com.alibaba.nacos.cmdb.service.CmdbWriter;
import com.alibaba.nacos.cmdb.utils.Loggers;
import com.alibaba.nacos.cmdb.utils.UtilsAndCommons;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author nkorange
 * @since 0.7.0
 */
@Component
public class CmdbProvider implements CmdbReader, CmdbWriter {

    @Autowired
    private SwitchAndOptions switches;

    private CmdbService cmdbService;

    ServiceLoader<CmdbService> serviceLoader = ServiceLoader.load(CmdbService.class);

    private Map<String, Map<String, Entity>> entityMap = new ConcurrentHashMap<>();

    private Map<String, Label> labelMap = new ConcurrentHashMap<>();

    private Set<String> entityTypeSet = new HashSet<>();

    private List<EntityEvent> eventList = new ArrayList<>();

    private long eventTimestamp = System.currentTimeMillis();

    public CmdbProvider() throws NacosException {
    }

    private void initCmdbService() throws NacosException {
        Iterator<CmdbService> iterator = serviceLoader.iterator();
        if (iterator.hasNext()) {
            cmdbService = iterator.next();
        }

        if (cmdbService == null && switches.isLoadDataAtStart()) {
            throw new NacosException(NacosException.SERVER_ERROR, "Cannot initialize CmdbService!");
        }
    }

    public void load() {

        if (!switches.isLoadDataAtStart()) {
            return;
        }

        // init label map:
        Set<String> labelNames = cmdbService.getLabelNames();
        if (labelNames == null || labelNames.isEmpty()) {
            Loggers.MAIN.warn("[LOAD] init label names failed!");
        } else {
            for (String labelName : labelNames) {
                // If get null label, it's still ok. We will try it later when we meet this label:
                labelMap.put(labelName, cmdbService.getLabel(labelName));
            }
        }

        // init entity type set:
        entityTypeSet = cmdbService.getEntityTypes();

        // init entity map:
        entityMap = cmdbService.getAllEntities();
    }

    @PostConstruct
    public void init() throws NacosException {

        initCmdbService();
        load();

        UtilsAndCommons.GLOBAL_EXECUTOR.schedule(new CmdbDumpTask(), switches.getDumpTaskInterval(), TimeUnit.SECONDS);
        UtilsAndCommons.GLOBAL_EXECUTOR.schedule(new CmdbLabelTask(), switches.getLabelTaskInterval(), TimeUnit.SECONDS);
        UtilsAndCommons.GLOBAL_EXECUTOR.schedule(new CmdbEventTask(), switches.getEventTaskInterval(), TimeUnit.SECONDS);
    }

    @Override
    public Entity queryEntity(String entityName, String entityType) {
        if (!entityMap.containsKey(entityType)) {
            return null;
        }
        return entityMap.get(entityType).get(entityName);
    }

    @Override
    public String queryLabel(String entityName, String entityType, String labelName) {
        Entity entity = queryEntity(entityName, entityType);
        if (entity == null) {
            return null;
        }
        return entity.getLabels().get(labelName);
    }

    @Override
    public List<Entity> queryEntitiesByLabel(String labelName, String labelValue) {
        throw new UnsupportedOperationException("Not available now!");
    }

    public void removeEntity(String entityName, String entityType) {
        if (!entityMap.containsKey(entityType)) {
            return;
        }
        entityMap.get(entityType).remove(entityName);
    }

    public void updateEntity(Entity entity) {
        if (!entityTypeSet.contains(entity.getType())) {
            return;
        }
        entityMap.get(entity.getType()).put(entity.getName(), entity);
    }

    public class CmdbLabelTask implements Runnable {

        @Override
        public void run() {

            Loggers.MAIN.debug("LABEL-TASK {}", "start dump.");

            if (cmdbService == null) {
                return;
            }

            try {

                Map<String, Label> tmpLabelMap = new HashMap<>(16);

                Set<String> labelNames = cmdbService.getLabelNames();
                if (labelNames == null || labelNames.isEmpty()) {
                    Loggers.MAIN.warn("CMDB-LABEL-TASK {}", "load label names failed!");
                } else {
                    for (String labelName : labelNames) {
                        // If get null label, it's still ok. We will try it later when we meet this label:
                        tmpLabelMap.put(labelName, cmdbService.getLabel(labelName));
                    }

                    if (Loggers.MAIN.isDebugEnabled()) {
                        Loggers.MAIN.debug("LABEL-TASK {}", "got label map:" + JSON.toJSONString(tmpLabelMap));
                    }

                    labelMap = tmpLabelMap;
                }

            } catch (Exception e) {
                Loggers.MAIN.error("CMDB-LABEL-TASK {}", "dump failed!", e);
            } finally {
                UtilsAndCommons.GLOBAL_EXECUTOR.schedule(this, switches.getLabelTaskInterval(), TimeUnit.SECONDS);
            }
        }
    }

    public class CmdbDumpTask implements Runnable {

        @Override
        public void run() {

            try {

                Loggers.MAIN.debug("DUMP-TASK {}", "start dump.");

                if (cmdbService == null) {
                    return;
                }
                // refresh entity map:
                entityMap = cmdbService.getAllEntities();
            } catch (Exception e) {
                Loggers.MAIN.error("DUMP-TASK {}", "dump failed!", e);
            } finally {
                UtilsAndCommons.GLOBAL_EXECUTOR.schedule(this, switches.getDumpTaskInterval(), TimeUnit.SECONDS);
            }
        }
    }

    public class CmdbEventTask implements Runnable {

        @Override
        public void run() {
            try {

                Loggers.MAIN.debug("EVENT-TASK {}", "start dump.");

                if (cmdbService == null) {
                    return;
                }

                long current = System.currentTimeMillis();
                List<EntityEvent> events = cmdbService.getEntityEvents(eventTimestamp);
                eventTimestamp = current;

                if (Loggers.MAIN.isDebugEnabled()) {
                    Loggers.MAIN.debug("EVENT-TASK {}", "got events size:" + ", events:" + JSON.toJSONString(events));
                }

                if (events != null && !events.isEmpty()) {

                    for (EntityEvent event : events) {
                        switch (event.getType()) {
                            case ENTITY_REMOVE:
                                removeEntity(event.getEntityName(), event.getEntityType());
                                break;
                            case ENTITY_ADD_OR_UPDATE:
                                updateEntity(cmdbService.getEntity(event.getEntityName(), event.getEntityType()));
                                break;
                            default:
                                break;
                        }
                    }
                }

            } catch (Exception e) {
                Loggers.MAIN.error("CMDB-EVENT {}", "event task failed!", e);
            } finally {
                UtilsAndCommons.GLOBAL_EXECUTOR.schedule(this, switches.getEventTaskInterval(), TimeUnit.SECONDS);
            }
        }
    }
}
