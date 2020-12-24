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

package com.alibaba.nacos.api.cmdb.spi;

import com.alibaba.nacos.api.cmdb.pojo.Entity;
import com.alibaba.nacos.api.cmdb.pojo.EntityEvent;
import com.alibaba.nacos.api.cmdb.pojo.Label;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service to visit CMDB store.
 *
 * @author nkorange
 * @since 0.7.0
 */
public interface CmdbService {
    
    /**
     * Get all label names stored in CMDB.
     *
     * @return label name set
     */
    Set<String> getLabelNames();
    
    /**
     * Get all possible entity types in CMDB.
     *
     * @return all entity types
     */
    Set<String> getEntityTypes();
    
    /**
     * Get label info.
     *
     * @param labelName label name
     * @return label info
     */
    Label getLabel(String labelName);
    
    /**
     * Get label value of label name of ip.
     *
     * @param entityName entity name
     * @param entityType entity type
     * @param labelName  target label name
     * @return label value
     */
    String getLabelValue(String entityName, String entityType, String labelName);
    
    /**
     * Get all label value of ip.
     *
     * @param entityName entity name
     * @param entityType entity type
     * @return all label values
     */
    Map<String, String> getLabelValues(String entityName, String entityType);
    
    /**
     * Dump all entities in CMDB.
     *
     * @return all entities
     */
    Map<String, Map<String, Entity>> getAllEntities();
    
    /**
     * get label change events.
     *
     * @param timestamp start time of generated events
     * @return label events
     */
    List<EntityEvent> getEntityEvents(long timestamp);
    
    /**
     * Get single entity.
     *
     * @param entityName name of entity
     * @param entityType type of entity
     * @return entity.
     */
    Entity getEntity(String entityName, String entityType);
}
