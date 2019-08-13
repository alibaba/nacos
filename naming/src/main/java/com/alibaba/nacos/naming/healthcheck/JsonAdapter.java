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
package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.nacos.api.naming.pojo.AbstractHealthChecker;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * @author nkorange
 */
public class JsonAdapter implements ObjectDeserializer, ObjectSerializer {

    private static JsonAdapter INSTANCE = new JsonAdapter();

    private JsonAdapter() {
    }

    public static JsonAdapter getInstance() {
        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        JSONObject jsonObj = (JSONObject) parser.parse();
        String checkType = jsonObj.getString("type");

        Class target = HealthCheckType.ofHealthCheckerClass(checkType);

        if(target != null){
            return (T) JSON.parseObject(jsonObj.toJSONString(), target);
        }

        return null;
    }

    @Override
    public int getFastMatchToken() {
        return 0;
    }

    @Override
    public void write(JSONSerializer jsonSerializer, Object o, Object o1, Type type, int i) throws IOException {
        SerializeWriter writer = jsonSerializer.getWriter();
        if (o == null) {
            writer.writeNull();
            return;
        }

        AbstractHealthChecker config = (AbstractHealthChecker) o;

        writer.writeFieldValue(',', "type", config.getType());

        config.jsonAdapterCallback(writer);
    }
}
