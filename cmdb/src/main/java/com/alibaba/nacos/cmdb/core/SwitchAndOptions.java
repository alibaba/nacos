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
package com.alibaba.nacos.cmdb.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author nkorange
 * @since 0.7.0
 */
@Component
public class SwitchAndOptions {

    @Value("${nacos.cmdb.dumpTaskInterval}")
    private int dumpTaskInterval;

    @Value("${nacos.cmdb.eventTaskInterval}")
    private int eventTaskInterval;

    @Value("${nacos.cmdb.labelTaskInterval}")
    private int labelTaskInterval;

    @Value("${nacos.cmdb.loadDataAtStart}")
    private boolean loadDataAtStart;

    public int getDumpTaskInterval() {
        return dumpTaskInterval;
    }

    public void setDumpTaskInterval(int dumpTaskInterval) {
        this.dumpTaskInterval = dumpTaskInterval;
    }

    public int getEventTaskInterval() {
        return eventTaskInterval;
    }

    public void setEventTaskInterval(int eventTaskInterval) {
        this.eventTaskInterval = eventTaskInterval;
    }

    public int getLabelTaskInterval() {
        return labelTaskInterval;
    }

    public void setLabelTaskInterval(int labelTaskInterval) {
        this.labelTaskInterval = labelTaskInterval;
    }

    public boolean isLoadDataAtStart() {
        return loadDataAtStart;
    }

    public void setLoadDataAtStart(boolean loadDataAtStart) {
        this.loadDataAtStart = loadDataAtStart;
    }
}
