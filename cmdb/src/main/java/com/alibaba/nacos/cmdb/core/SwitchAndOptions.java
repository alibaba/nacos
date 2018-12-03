package com.alibaba.nacos.cmdb.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@Component
public class SwitchAndOptions {

    @Value("${nacos.cmdb.dumpTaskInterval}")
    private int dumpTaskInterval;

    @Value("${nacos.cmdb.eventTaskInterval}")
    private int eventTaskInterval;

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

    public boolean isLoadDataAtStart() {
        return loadDataAtStart;
    }

    public void setLoadDataAtStart(boolean loadDataAtStart) {
        this.loadDataAtStart = loadDataAtStart;
    }
}
