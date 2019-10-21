package com.alibaba.nacos.naming.selector;

import com.alibaba.nacos.api.exception.NacosException;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Set;

/**
 * @author Xs.Tao
 */
public class LabelSelectorTest {

    private String expression = "CONSUMER.label.A=PROVIDER.label.A &CONSUMER.label.B=PROVIDER.label.B";

    @Test
    public void parseExpression() throws NacosException {
        expression = StringUtils.deleteWhitespace(expression);
        List<String> terms =LabelSelector.ExpressionInterpreter.getTerms(expression);
        Assert.assertEquals(7,terms.size());
        Set<String> parseLables=LabelSelector.parseExpression(expression);
        Assert.assertEquals(2,parseLables.size());
        String[] labs=parseLables.toArray(new String[]{});
        Assert.assertEquals("A",labs[0]);
        Assert.assertEquals("B",labs[1]);
    }

}
