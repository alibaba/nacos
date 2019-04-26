package com.alibaba.nacos.api.selector;

/**
 * @author liaochuntao
 * @since 1.0.0
 */
public class NoneSelector extends AbstractSelector {

    public NoneSelector() {
        this.setType(SelectorType.none.name());
    }
}
