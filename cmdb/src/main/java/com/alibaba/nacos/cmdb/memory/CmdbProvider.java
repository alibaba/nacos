package com.alibaba.nacos.cmdb.memory;

import com.alibaba.nacos.api.cmdb.CmdbService;
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
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
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
        initCmdbService();
        load();
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

        // TODO load data on disk:

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
        entityMap = cmdbService.dumpAllEntities();
    }

    @PostConstruct
    public void init() {
        UtilsAndCommons.GLOBAL_EXECUTOR.schedule(new CmdbDumpTask(), switches.getDumpTaskInterval(), TimeUnit.SECONDS);
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

    public class CmdbDumpTask implements Runnable {

        @Override
        public void run() {
            try {
                // refresh entity map:
                entityMap = cmdbService.dumpAllEntities();
            } catch (Exception e) {
                Loggers.MAIN.error("CMDB-DUMP {}", "dump failed!", e);
            } finally {
                UtilsAndCommons.GLOBAL_EXECUTOR.schedule(this, switches.getDumpTaskInterval(), TimeUnit.SECONDS);
            }
        }
    }

    public class CmdbEventTask implements Runnable {

        @Override
        public void run() {
            try {
                long current = System.currentTimeMillis();
                List<EntityEvent> events = cmdbService.getLabelEvents(eventTimestamp);
                eventTimestamp = current;

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
