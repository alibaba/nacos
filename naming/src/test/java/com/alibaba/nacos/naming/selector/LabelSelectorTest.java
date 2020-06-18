/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.naming.selector;

import com.alibaba.nacos.api.exception.NacosException;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class LabelSelectorTest {
    
    private String expression = "CONSUMER.label.A=PROVIDER.label.A &CONSUMER.label.B=PROVIDER.label.B";
    
    @Test
    public void parseExpression() throws NacosException {
        expression = StringUtils.deleteWhitespace(expression);
        List<String> terms = LabelSelector.ExpressionInterpreter.getTerms(expression);
        Assert.assertEquals(7, terms.size());
        Set<String> parseLables = LabelSelector.parseExpression(expression);
        Assert.assertEquals(2, parseLables.size());
        String[] labs = parseLables.toArray(new String[] {});
        Assert.assertEquals("A", labs[0]);
        Assert.assertEquals("B", labs[1]);
    }
    
}
