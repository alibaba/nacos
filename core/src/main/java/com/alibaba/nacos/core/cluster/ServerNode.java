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

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ServerNode implements Node {

    private String ip;

    private int port;

    private NodeState state;

    private Map<String, String> extendInfo = new HashMap<>();

    private String address;

    {
        extendInfo.put(SITE_KEY, "unknown");
        extendInfo.put(AD_WEIGHT, "0");
        extendInfo.put(LAST_REF_TIME, "0");
        extendInfo.put(WEIGHT, "1");
        extendInfo.put(DISTRO_BEATS, null);
    }

    @Override
    public void setIp(String ip) {
        this.ip = ip;
    }

    @Override
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public void setState(NodeState state) {
        this.state = state;
    }

    @Override
    public String ip() {
        return ip;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public String address() {
        if (StringUtils.isBlank(address)) {
            address = ip + ":" + port;
        }
        return address;
    }

    @Override
    public NodeState state() {
        return state;
    }

    @Override
    public Map<String, String> extendInfo() {
        return extendInfo;
    }

    @Override
    public String extendVal(String key) {
        return extendInfo.get(key);
    }

    @Override
    public void setExtendVal(String key, String value) {
        extendInfo.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ServerNode that = (ServerNode) o;
        if (StringUtils.isAnyBlank(address, that.address)) {
            return port == that.port &&
                    Objects.equals(ip, that.ip);
        }
        return StringUtils.equals(address, that.address);
    }

    @Override
    public String toString() {
        return "ServerNode{" +
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

    public static ServerNodeBuilder builder() {
        return new ServerNodeBuilder();
    }

    public static final class ServerNodeBuilder {
        private String ip;
        private int port;
        private NodeState state;
        private Map<String, String> extendInfo;
        private String address;

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

        public ServerNode build() {
            ServerNode serverNode = new ServerNode();
            serverNode.extendInfo.putAll(this.extendInfo);
            serverNode.state = this.state;
            serverNode.ip = this.ip;
            serverNode.port = this.port;
            serverNode.address = this.ip + ":" + this.port;
            return serverNode;
        }
    }
}
