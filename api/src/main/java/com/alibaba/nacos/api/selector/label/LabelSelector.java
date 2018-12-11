package com.alibaba.nacos.api.selector.label;

import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.SelectorType;

/**
 * The selector to filter resource with flexible expression.
 *
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class LabelSelector extends AbstractSelector {

    /**
     * Label expression of this selector.
     */
    private String expression;

    public LabelSelector() {
        this.setType(SelectorType.label.name());
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }
}
