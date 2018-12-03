package com.alibaba.nacos.api.cmdb.pojo;

import java.util.Map;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class Entity {

    private String type;
    private String name;
    private Map<String, String> labels;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
}
