package com.alibaba.nacos.api.selector;

/**
 * The types of selector accepted by Nacos
 *
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public enum SelectorType {
    /**
     * not match any type
     */
    unknown,
    /**
     * not filter out any entity
     */
    none,
    /**
     * select by label
     */
    label
}
