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

import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface Member {

    String SITE_KEY = "site";

    String AD_WEIGHT = "adweight";

    String LAST_REF_TIME = "lastRefTime";

    String WEIGHT = "weight";

    String DISTRO_BEATS = "distroBeats";

    /**
     * update node ip info
     *
     * @param ip server ip
     */
    void setIp(String ip);

    /**
     * update node port info;
     *
     * @param port server port
     */
    void setPort(int port);

    /**
     * update node state
     *
     * @param state {@link NodeState}
     */
    void setState(NodeState state);

    /**
     * get this node ip info
     *
     * @return ip info
     */
    String ip();

    /**
     * get this node port info
     *
     * @return port info
     */
    int port();

    /**
     * return ip：port
     *
     * @return ip:port
     */
    String address();

    /**
     * get this node state info
     *
     * @return state info
     */
    NodeState state();

    /**
     * get this node extend info
     *
     * @return all extend info
     */
    Map<String, Object> extendInfo();

    /**
     * get extend data by key
     *
     * @param key extend info key
     * @return get target extend info
     */
    Object extendVal(String key);

    /**
     * set extend data by key
     *
     * @param key extend info key
     * @param value extend value
     */
    void setExtendVal(String key, Object value);

    /**
     * Verify node information
     *
     * @return Check result
     */
    boolean check();

}
