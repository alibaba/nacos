package com.alibaba.nacos.common.notify;

import java.io.Serializable;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author zongtanghu
 *
 */
public interface Event extends Serializable {

    /**
     * Event sequence number, which can be used to handle the sequence of events
     *
     * @return sequence num, It's best to make sure it's monotone
     */
    default long sequence() {
        return System.currentTimeMillis();
    }

}
