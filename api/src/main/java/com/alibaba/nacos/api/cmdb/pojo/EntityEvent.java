package com.alibaba.nacos.api.cmdb.pojo;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class EntityEvent {

    private EntityEventType type;
    private String entityName;
    private String entityType;

    public EntityEventType getType() {
        return type;
    }

    public void setType(EntityEventType type) {
        this.type = type;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }
}
