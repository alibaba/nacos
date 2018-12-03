package com.alibaba.nacos.api.cmdb.pojo;

import java.util.Set;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class Label {

    private String name;
    private Set<String> values;
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<String> getValues() {
        return values;
    }

    public void setValues(Set<String> values) {
        this.values = values;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
