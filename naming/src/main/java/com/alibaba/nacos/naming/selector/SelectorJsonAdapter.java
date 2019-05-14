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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.nacos.api.selector.SelectorType;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Json adapter for class Selector.
 * <p>
 * When deserializing object for class Selector, we should consider to convert the selector to
 * its runtime child class object. And this adapter helps us to finish it.
 *
 * @author nkorange
 * @since 0.7.0
 */
public class SelectorJsonAdapter implements ObjectDeserializer, ObjectSerializer {

    private static SelectorJsonAdapter INSTANCE = new SelectorJsonAdapter();

    private SelectorJsonAdapter() {
    }

    public static SelectorJsonAdapter getInstance() {
        return INSTANCE;
    }

    @Override
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {

        JSONObject jsonObj = (JSONObject) parser.parse();

        if (jsonObj == null) {
            return null;
        }

        String checkType = jsonObj.getString("type");

        if (StringUtils.equals(checkType, SelectorType.label.name())) {
            return (T) JSON.parseObject(jsonObj.toJSONString(), LabelSelector.class);
        }

        if (StringUtils.equals(checkType, SelectorType.none.name())) {
            return (T) JSON.parseObject(jsonObj.toJSONString(), NoneSelector.class);
        }

        return null;
    }

    @Override
    public int getFastMatchToken() {
        return 0;
    }

    @Override
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {

        SerializeWriter writer = serializer.getWriter();
        if (object == null) {
            writer.writeNull();
            return;
        }

        Selector selector = (Selector) object;

        writer.writeFieldValue(',', "type", selector.getType());

        if (StringUtils.equals(selector.getType(), SelectorType.label.name())) {
            LabelSelector labelSelector = (LabelSelector) selector;
            writer.writeFieldValue(',', "labels", JSON.toJSONString(labelSelector.getLabels()));
        }
    }
}
