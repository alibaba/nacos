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
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author nacos
 */
public abstract class AbstractHealthCheckConfig implements Cloneable {

    protected String type = "unknown";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Get copy of health check config
     *
     * @return Copy of health check config
     * @throws CloneNotSupportedException
     */
    @Override
    public abstract AbstractHealthCheckConfig clone() throws CloneNotSupportedException;

    public static class Http extends AbstractHealthCheckConfig {
        public static final String TYPE = "HTTP";
        public static final String HTTP_HEADER_SPLIT_STRING = "\\|";

        private String path = StringUtils.EMPTY;
        private String headers = StringUtils.EMPTY;

        private int expectedResponseCode = 200;

        public Http() {
            this.type = TYPE;
        }

        public int getExpectedResponseCode() {
            return expectedResponseCode;
        }

        public void setExpectedResponseCode(int expectedResponseCode) {
            this.expectedResponseCode = expectedResponseCode;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getHeaders() {
            return headers;
        }

        public void setHeaders(String headers) {
            this.headers = headers;
        }

        @JSONField(serialize = false)
        public Map<String, String> getCustomHeaders() {
            if (StringUtils.isBlank(headers)) {
                return Collections.emptyMap();
            }

            Map<String, String> headers = new HashMap<String, String>(this.headers.split(HTTP_HEADER_SPLIT_STRING).length);
            for (String s : this.headers.split(HTTP_HEADER_SPLIT_STRING)) {
                String[] splits = s.split(":");
                if (splits.length != 2) {
                    continue;
                }

                headers.put(StringUtils.trim(splits[0]), StringUtils.trim(splits[1]));
            }

            return headers;
        }

        @Override
        public int hashCode() {
            return Objects.hash(headers, path, expectedResponseCode);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Http)) {
                return false;
            }

            Http other = (Http) obj;

            if (!StringUtils.equals(type, other.getType())) {
                return false;
            }

            if (!StringUtils.equals(path, other.getPath())) {
                return false;
            }
            if (!StringUtils.equals(headers, other.getHeaders())) {
                return false;
            }
            return expectedResponseCode == other.getExpectedResponseCode();

        }

        @SuppressFBWarnings("CN_IDIOM_NO_SUPER_CALL")
        @Override
        public AbstractHealthCheckConfig.Http clone() throws CloneNotSupportedException {
            AbstractHealthCheckConfig.Http config = new AbstractHealthCheckConfig.Http();

            config.setPath(this.path);
            config.setHeaders(this.headers);
            config.setType(this.type);
            config.setExpectedResponseCode(this.expectedResponseCode);

            return config;
        }
    }

    public static class Tcp extends AbstractHealthCheckConfig {
        public static final String TYPE = "TCP";

        public Tcp() {
            this.type = TYPE;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Tcp.TYPE);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Tcp;

        }

        @SuppressFBWarnings("CN_IDIOM_NO_SUPER_CALL")
        @Override
        public AbstractHealthCheckConfig.Tcp clone() throws CloneNotSupportedException {
            AbstractHealthCheckConfig.Tcp config = new AbstractHealthCheckConfig.Tcp();

            config.setType(this.type);

            return config;
        }
    }

    public static class Mysql extends AbstractHealthCheckConfig {
        public static final String TYPE = "MYSQL";

        private String user;
        private String pwd;
        private String cmd;

        public Mysql() {
            this.type = TYPE;
        }

        public String getCmd() {
            return cmd;
        }

        public String getPwd() {
            return pwd;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public void setCmd(String cmd) {
            this.cmd = cmd;
        }

        public void setPwd(String pwd) {
            this.pwd = pwd;
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, pwd, cmd);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Mysql)) {
                return false;
            }

            Mysql other = (Mysql) obj;

            if (!StringUtils.equals(user, other.getUser())) {
                return false;
            }

            if (!StringUtils.equals(pwd, other.getPwd())) {
                return false;
            }

            return StringUtils.equals(cmd, other.getCmd());

        }

        @SuppressFBWarnings("CN_IDIOM_NO_SUPER_CALL")
        @Override
        public AbstractHealthCheckConfig.Mysql clone() throws CloneNotSupportedException {
            AbstractHealthCheckConfig.Mysql config = new AbstractHealthCheckConfig.Mysql();

            config.setUser(this.user);
            config.setPwd(this.pwd);
            config.setCmd(this.cmd);
            config.setType(this.type);

            return config;
        }
    }

    public static class JsonAdapter implements ObjectDeserializer, ObjectSerializer {
        private static JsonAdapter INSTANCE = new JsonAdapter();

        private JsonAdapter() {
        }

        ;

        public static JsonAdapter getInstance() {
            return INSTANCE;
        }

        @Override
        public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
            JSONObject jsonObj = (JSONObject) parser.parse();
            String checkType = jsonObj.getString("type");

            if (StringUtils.equals(checkType, AbstractHealthCheckConfig.Http.TYPE)) {
                return (T) JSON.parseObject(jsonObj.toJSONString(), AbstractHealthCheckConfig.Http.class);
            }

            if (StringUtils.equals(checkType, AbstractHealthCheckConfig.Tcp.TYPE)) {
                return (T) JSON.parseObject(jsonObj.toJSONString(), AbstractHealthCheckConfig.Tcp.class);
            }

            if (StringUtils.equals(checkType, AbstractHealthCheckConfig.Mysql.TYPE)) {
                return (T) JSON.parseObject(jsonObj.toJSONString(), AbstractHealthCheckConfig.Mysql.class);
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

            AbstractHealthCheckConfig config = (AbstractHealthCheckConfig) o;

            writer.writeFieldValue(',', "type", config.getType());

            if (StringUtils.equals(config.getType(), HealthCheckType.HTTP.name())) {
                AbstractHealthCheckConfig.Http httpCheckConfig = (Http) config;
                writer.writeFieldValue(',', "path", httpCheckConfig.getPath());
                writer.writeFieldValue(',', "headers", httpCheckConfig.getHeaders());
            }

            if (StringUtils.equals(config.getType(), HealthCheckType.TCP.name())) {
                // nothing sepcial to handle
            }

            if (StringUtils.equals(config.getType(), HealthCheckType.MYSQL.name())) {
                AbstractHealthCheckConfig.Mysql mysqlCheckConfig = (Mysql) config;
                writer.writeFieldValue(',', "user", mysqlCheckConfig.getUser());
                writer.writeFieldValue(',', "pwd", mysqlCheckConfig.getPwd());
                writer.writeFieldValue(',', "cmd", mysqlCheckConfig.getCmd());
            }
        }
    }
}
