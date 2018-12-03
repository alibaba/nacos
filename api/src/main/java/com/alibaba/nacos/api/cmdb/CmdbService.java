package com.alibaba.nacos.api.cmdb;

import com.alibaba.nacos.api.cmdb.pojo.Entity;
import com.alibaba.nacos.api.cmdb.pojo.EntityEvent;
import com.alibaba.nacos.api.cmdb.pojo.Label;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public interface CmdbService {

    /**
     * Get all label names stored in CMDB
     *
     * @return label name set
     */
    Set<String> getLabelNames();

    /**
     * Get all possible entity types in CMDB
     *
     * @return all entity types
     */
    Set<String> getEntityTypes();

    /**
     * Get label info
     *
     * @param labelName label name
     * @return label info
     */
    Label getLabel(String labelName);

    /**
     * Get label value of label name of ip
     *
     * @param entityType  entity type
     * @param entityValue entity value
     * @param labelName   target label name
     * @return label value
     */
    String getLabelValue(String entityValue, String entityType, String labelName);

    /**
     * Get all label value of ip
     *
     * @param entityType  entity type
     * @param entityValue entity value
     * @return all label values
     */
    Map<String, String> getLabelValues(String entityValue, String entityType);

    /**
     * Dump all entities in CMDB
     *
     * @return all entities
     */
    Map<String, Map<String, Entity>> dumpAllEntities();

    /**
     * get label change events
     *
     * @param timestamp start time of generated events
     * @return label events
     */
    List<EntityEvent> getLabelEvents(long timestamp);

    /**
     * Get single entity
     *
     * @param entityName name of entity
     * @param entityType type of entity
     * @return
     */
    Entity getEntity(String entityName, String entityType);
}
