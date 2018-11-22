package com.alibaba.nacos.cmdb.plugin.spi;

import com.alibaba.nacos.cmdb.pojo.Entity;
import com.alibaba.nacos.cmdb.pojo.Label;
import com.alibaba.nacos.cmdb.pojo.LabelEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Main service to interact with third-party CMDB.
 *
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public interface ExternalCmdbService {

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
    Map<String, Set<Entity>> dumpAllEntities();

    /**
     * get label change events
     *
     * @return label events
     */
    List<LabelEvent> getLabelEvents();
}
