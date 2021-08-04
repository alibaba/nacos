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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.naming.selector.v1.LabelSelector;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
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
    
    public static void main(String[] args) throws IOException {
        FileInputStream fileInputStream = new FileInputStream("C:\\Users\\admin\\Desktop\\酷家乐\\test");
        byte[] bytes = new byte[(int) fileInputStream.getChannel().size()];
        fileInputStream.read(bytes);
        Serializer serializer = SerializeFactory.getDefault();
        com.alibaba.nacos.naming.selector.LabelSelector labelSelector = serializer.deserialize(bytes);
        System.out.println(labelSelector.getLabels());
        System.out.println(labelSelector.getExpression());
        
        
        
//        com.alibaba.nacos.naming.selector.LabelSelector labelSelector = new com.alibaba.nacos.naming.selector.LabelSelector();
//        labelSelector.setExpression("aaa");
//        labelSelector.setLabels(Collections.singleton("bbb"));
//
//        Serializer serializer = SerializeFactory.getDefault();
//        byte[] bytes = serializer.serialize(labelSelector);
//
//        File file = new File("C:\\Users\\admin\\Desktop\\酷家乐\\test");
//        file.createNewFile();
//        FileOutputStream fileWriter = new FileOutputStream("C:\\Users\\admin\\Desktop\\酷家乐\\test");
//        fileWriter.write(bytes);
    }
}
