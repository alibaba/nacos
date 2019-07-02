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
package com.alibaba.nacos.naming.acl;

import java.nio.charset.StandardCharsets;

/**
 * @author nkorange
 */
public class AuthInfo {
    private String operator;

    private String appKey;

    public AuthInfo() {}

    public AuthInfo(String operator, String appKey) {
        this.operator = operator;
        this.appKey = appKey;
    }

    public static AuthInfo fromString(String auth, String encoding) {
        try {
            String[] byteStrs = auth.split(",");
            byte[] bytes = new byte[byteStrs.length];
            for(int i = 0; i < byteStrs.length; i++) {
                bytes[i] = (byte)(~(Short.parseShort(byteStrs[i])));
            }

            String contentStr = new String(bytes, encoding);
            String[] params = contentStr.split(":");
            return new AuthInfo(params[0], params[1]);
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public String toString() {
        try {
            // very simple encryption is enough
            byte[] authBytes = (operator + ":" + appKey).getBytes(StandardCharsets.UTF_8);
            StringBuilder authBuilder = new StringBuilder();
            for (byte authByte : authBytes) {
                authBuilder.append((byte) (~((short) authByte))).append(",");
            }

            return authBuilder.substring(0, authBuilder.length() - 1);
        } catch (Exception e) {
            return "Error while encrypt AuthInfo" + e;
        }
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getOperator() {
        return operator;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }
}
