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

package com.alibaba.nacos.core.cluster;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Member {

    public static final String SITE_KEY = "site";

    public static final String AD_WEIGHT = "adweight";

    public static final String LAST_REF_TIME = "lastRefTime";

    public static final String WEIGHT = "weight";

    public static final String DISTRO_BEATS = "distroBeats";

    private String ip;

    private int port = -1;

    private NodeState state;

    private Map<String, Object> extendInfo = new HashMap<>();

    private String address = "";

    public Member() {
        extendInfo.put(SITE_KEY, "unknown");
        extendInfo.put(AD_WEIGHT, "0");
        extendInfo.put(LAST_REF_TIME, "0");
        extendInfo.put(WEIGHT, "1");
        extendInfo.put(DISTRO_BEATS, null);
    }

    public static ServerNodeBuilder builder() {
        return new ServerNodeBuilder();
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public NodeState getState() {
        return state;
    }

    public void setState(NodeState state) {
        this.state = state;
    }

    public Map<String, Object> getExtendInfo() {
        return extendInfo;
    }

    public void setExtendInfo(Map<String, Object> extendInfo) {
        this.extendInfo = extendInfo;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String ip() {
        return ip;
    }

    public int port() {
        return port;
    }

    public String address() {
        if (StringUtils.isBlank(address)) {
            address = ip + ":" + port;
        }
        return address;
    }

    public NodeState state() {
        return state;
    }

    public Map<String, Object> extendInfo() {
        return extendInfo;
    }

    public Object extendVal(String key) {
        return extendInfo.get(key);
    }

    public void setExtendVal(String key, Object value) {
        extendInfo.put(key, value);
    }

    public boolean check() {
        return StringUtils.isNoneBlank(ip, address) && port != -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Member that = (Member) o;
        if (StringUtils.isAnyBlank(address, that.address)) {
            return port == that.port &&
                    Objects.equals(ip, that.ip);
        }
        return StringUtils.equals(address, that.address);
    }

    @Override
    public String toString() {
        return "Member{" +
                "ip='" + ip + '\'' +
                ", port=" + port +
                ", state=" + state +
                ", extendInfo=" + extendInfo +
                ", address='" + address + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }

    public static final class ServerNodeBuilder {
        private String ip;
        private int port;
        private NodeState state;
        private Map<String, String> extendInfo;

        private ServerNodeBuilder() {
        }

        public ServerNodeBuilder ip(String ip) {
            this.ip = ip;
            return this;
        }

        public ServerNodeBuilder port(int port) {
            this.port = port;
            return this;
        }

        public ServerNodeBuilder state(NodeState state) {
            this.state = state;
            return this;
        }

        public ServerNodeBuilder extendInfo(Map<String, String> extendInfo) {
            this.extendInfo = extendInfo;
            return this;
        }

        public Member build() {
            Member serverNode = new Member();
            serverNode.extendInfo.putAll(this.extendInfo);
            serverNode.state = this.state;
            serverNode.ip = this.ip;
            serverNode.port = this.port;
            serverNode.address = this.ip + ":" + this.port;
            return serverNode;
        }
    }

}
