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

package com.alibaba.nacos.core.distributed.distro.event;

import com.alibaba.nacos.core.notify.Event;

import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ReceiveChecksumsEvent implements Event {

    private Map<String, Map<String, String>> checksumMap;
    private String server;

    public Map<String, Map<String, String>> getChecksumMap() {
        return checksumMap;
    }

    public void setChecksumMap(Map<String, Map<String, String>> checksumMap) {
        this.checksumMap = checksumMap;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    @Override
    public Class<? extends Event> eventType() {
        return ReceiveChecksumsEvent.class;
    }



    @Override
    public String toString() {
        return "ReceiveChecksumsEvent{" +
                "checksumMap=" + checksumMap +
                ", server='" + server + '\'' +
                '}';
    }

    public static ReceiveChecksumsEventBuilder builder() {
        return new ReceiveChecksumsEventBuilder();
    }

    public static final class ReceiveChecksumsEventBuilder {
        private Map<String, Map<String, String>> checksumMap;
        private String server;

        private ReceiveChecksumsEventBuilder() {
        }

        public ReceiveChecksumsEventBuilder checksumMap(Map<String, Map<String, String>> checksumMap) {
            this.checksumMap = checksumMap;
            return this;
        }

        public ReceiveChecksumsEventBuilder server(String server) {
            this.server = server;
            return this;
        }

        public ReceiveChecksumsEvent build() {
            ReceiveChecksumsEvent receiveChecksumsEvent = new ReceiveChecksumsEvent();
            receiveChecksumsEvent.setChecksumMap(checksumMap);
            receiveChecksumsEvent.setServer(server);
            return receiveChecksumsEvent;
        }
    }
}
