package com.alibaba.nacos.cmdb.service;

import com.alibaba.nacos.cmdb.pojo.Entity;

import java.util.List;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public interface CmdbReader {

    /**
     * Get entity
     *
     * @param entityName name of entity
     * @param entityType type of entity
     * @return entity
     */
    Entity queryEntity(String entityName, String entityType);

    /**
     * Get label of entity
     *
     * @param entityName name of entity
     * @param entityType type of entity
     * @param labelName  label name
     * @return label value
     */
    String queryLabel(String entityName, String entityType, String labelName);

    /**
     * Get entities of selected label
     *
     * @param labelName  name of label
     * @param labelValue value of label
     * @return list of entiy
     */
    List<Entity> queryEntitiesByLabel(String labelName, String labelValue);
}
