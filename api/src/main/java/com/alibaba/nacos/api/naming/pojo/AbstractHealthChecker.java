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
package com.alibaba.nacos.api.naming.pojo;

import java.util.Objects;

/**
 * @author dungu.zpf
 */
public abstract class AbstractHealthChecker implements Cloneable {

    protected String type = "unknown";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public static class Http extends AbstractHealthChecker {
        public static final String TYPE = "HTTP";

        private String path = "";
        private String headers = "";

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

        @Override
        public int hashCode() {
            return Objects.hash(path, headers, expectedResponseCode);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Http)) {
                return false;
            }

            Http other = (Http) obj;

            if (!strEquals(type, other.getType())) {
                return false;
            }

            if (!strEquals(path, other.getPath())) {
                return false;
            }
            if (!strEquals(headers, other.getHeaders())) {
                return false;
            }
            return expectedResponseCode == other.getExpectedResponseCode();
        }
    }

    public static class Tcp extends AbstractHealthChecker {
        public static final String TYPE = "TCP";

        public Tcp() {
            this.type = TYPE;
        }

        @Override
        public int hashCode() {
            return Objects.hash(TYPE);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Tcp;

        }
    }

    public static class Mysql extends AbstractHealthChecker {
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

            if (!strEquals(user, other.getUser())) {
                return false;
            }

            if (!strEquals(pwd, other.getPwd())) {
                return false;
            }

            return strEquals(cmd, other.getCmd());

        }
    }
    
	private static boolean strEquals(String str1, String str2) {
		return str1 == null ? str2 == null : str1.equals(str2);
	}
}
