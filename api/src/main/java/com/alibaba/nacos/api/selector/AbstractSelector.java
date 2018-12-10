package com.alibaba.nacos.api.selector;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public abstract class AbstractSelector {

    private String type;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
