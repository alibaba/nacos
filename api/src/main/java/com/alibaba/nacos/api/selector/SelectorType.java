package com.alibaba.nacos.api.selector;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public enum SelectorType {
    /**
     * not match any type
     */
    unknown,
    /**
     * not filter out
     */
    none,
    /**
     * select by label
     */
    label
}
