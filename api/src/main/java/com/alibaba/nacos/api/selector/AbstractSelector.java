package com.alibaba.nacos.api.selector;

/**
 * Abstract selector that only contains a type
 *
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public abstract class AbstractSelector {

    /**
     * The type of this selector, each child class should announce its own unique type.
     */
    private String type;

    public String getType() {
        return type;
    }

    protected void setType(String type) {
        this.type = type;
    }
}
