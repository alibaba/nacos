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

package com.alibaba.nacos.core.file;

import com.alibaba.nacos.core.notify.Event;
import java.nio.file.WatchEvent;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class FileChangeEvent implements Event {

    private static final long serialVersionUID = -4255584033113954765L;

    private String paths;

    private WatchEvent<?> event;

    public static FileChangeEventBuilder builder() {
        return new FileChangeEventBuilder();
    }

    public String getPaths() {
        return paths;
    }

    public void setPaths(String paths) {
        this.paths = paths;
    }

    public WatchEvent<?> getEvent() {
        return event;
    }

    public void setEvent(WatchEvent<?> event) {
        this.event = event;
    }

    @Override
    public String toString() {
        return "FileChangeEvent{" +
                "paths='" + paths + '\'' +
                ", event-kind=" + event.kind() + '\'' +
                ", even-context=" + event.context() + '\'' +
                '}';
    }

    @Override
    public Class<? extends Event> eventType() {
        return FileChangeEvent.class;
    }

    public static final class FileChangeEventBuilder {
        private String paths;
        private WatchEvent<?> event;

        private FileChangeEventBuilder() {
        }

        public FileChangeEventBuilder paths(String paths) {
            this.paths = paths;
            return this;
        }

        public FileChangeEventBuilder event(WatchEvent<?> event) {
            this.event = event;
            return this;
        }

        public FileChangeEvent build() {
            FileChangeEvent fileChangeEvent = new FileChangeEvent();
            fileChangeEvent.setPaths(paths);
            fileChangeEvent.setEvent(event);
            return fileChangeEvent;
        }
    }
}
