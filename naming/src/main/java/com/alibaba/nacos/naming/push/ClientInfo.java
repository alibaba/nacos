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

package com.alibaba.nacos.naming.push;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.util.VersionUtil;

/**
 * Client info.
 *
 * @author nacos
 */
public class ClientInfo {

    public Version version;

    public ClientType type;

    public ClientInfo(String userAgent) {
        String versionStr = StringUtils.isEmpty(userAgent) ? StringUtils.EMPTY : userAgent;
        if (com.alibaba.nacos.common.utils.StringUtils.isBlank(versionStr)) {
            //we're not eager to implement other type yet
            this.type = ClientType.UNKNOWN;
            this.version = Version.unknownVersion();
            return;
        }
        versionStr = versionStr.substring(versionStr.indexOf(":v") + 2);
        version = VersionUtil.parseVersion(versionStr);
        type = ClientType.getDes(versionStr);
    }

    public enum ClientType {
        /**
         * Go client type.
         */
        GO("Nacos-Go-Client"),
        /**
         * Java client type.
         */
        JAVA("Nacos-Java-Client"),
        /**
         * C client type.
         */
        C("Nacos-C-Client,vip-client4cpp"),
        /**
         * CSharp client type.
         */
        CSHARP("Nacos-CSharp-Client"),
        /**
         * php client type.
         */
        PHP("PHP"),
        /**
         * dns-f client type.
         */
        DNS("Nacos-DNS"),
        /**
         * nginx client type.
         */
        TENGINE("unit-nginx"),
        /**
         * sdk client type.
         */
        JAVA_SDK("Nacos-SDK-Java"),
        /**
         * Server notify each other.
         */
        NACOS_SERVER("Nacos-Server"),
        /**
         * Unknown client type.
         */
        UNKNOWN("");
        private String des;

        ClientType(String des) {
            this.des = des;
        }

        public static ClientType getDes(String ver) {
            for (ClientType clientType : ClientType.values()) {
                for (String key : clientType.des.split(",")) {
                    if (ver.startsWith(key)) {
                        return clientType;
                    }
                }
            }
            return UNKNOWN;
        }
    }
}
