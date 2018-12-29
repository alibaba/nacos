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
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class JsonAdapter implements ObjectDeserializer, ObjectSerializer {

    private static JsonAdapter INSTANCE = new JsonAdapter();

    private JsonAdapter() {
    }

    public static JsonAdapter getInstance() {
        return INSTANCE;
    }

    @Override
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        JSONObject jsonObj = (JSONObject) parser.parse();
        String checkType = jsonObj.getString("type");

        if (StringUtils.equals(checkType, AbstractHealthChecker.Http.TYPE)) {
            return (T) JSON.parseObject(jsonObj.toJSONString(), AbstractHealthChecker.Http.class);
        }

        if (StringUtils.equals(checkType, AbstractHealthChecker.Tcp.TYPE)) {
            return (T) JSON.parseObject(jsonObj.toJSONString(), AbstractHealthChecker.Tcp.class);
        }

        if (StringUtils.equals(checkType, AbstractHealthChecker.Mysql.TYPE)) {
            return (T) JSON.parseObject(jsonObj.toJSONString(), AbstractHealthChecker.Mysql.class);
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

        if (StringUtils.equals(config.getType(), HealthCheckType.HTTP.name())) {
            AbstractHealthChecker.Http httpCheckConfig = (AbstractHealthChecker.Http) config;
            writer.writeFieldValue(',', "path", httpCheckConfig.getPath());
            writer.writeFieldValue(',', "headers", httpCheckConfig.getHeaders());
        }

        if (StringUtils.equals(config.getType(), HealthCheckType.TCP.name())) {
            // nothing sepcial to handle
        }

        if (StringUtils.equals(config.getType(), HealthCheckType.MYSQL.name())) {
            AbstractHealthChecker.Mysql mysqlCheckConfig = (AbstractHealthChecker.Mysql) config;
            writer.writeFieldValue(',', "user", mysqlCheckConfig.getUser());
            writer.writeFieldValue(',', "pwd", mysqlCheckConfig.getPwd());
            writer.writeFieldValue(',', "cmd", mysqlCheckConfig.getCmd());
        }
    }
}
