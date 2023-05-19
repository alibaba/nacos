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

package com.alibaba.nacos.consistency;

import java.io.Serializable;
import java.util.Set;

/**
 * Consistent protocol related configuration objects.
 *
 * <p>{@link RequestProcessor} : The consistency protocol provides services for all businesses, but each business only cares
 * about the transaction information belonging to that business, and the transaction processing between the various
 * services should not block each other. Therefore, the LogProcessor is abstracted to implement the parallel processing
 * of transactions of different services. Corresponding LogProcessor sub-interface: LogProcessor4AP or LogProcessor4CP,
 * different consistency protocols will actively discover the corresponding LogProcessor
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface Config<L extends RequestProcessor> extends Serializable {
    
    /**
     * Set the cluster node information to initialize，like [ip:port, ip:port, ip:port].
     *
     * @param self    local node address information, ip:port
     * @param members {@link Set}
     */
    void setMembers(String self, Set<String> members);
    
    /**
     * members join.
     *
     * @param members {@link Set}
     */
    void addMembers(Set<String> members);
    
    /**
     * members leave.
     *
     * @param members {@link Set}
     */
    void removeMembers(Set<String> members);
    
    /**
     * get local node address info.
     *
     * @return address
     */
    String getSelfMember();
    
    /**
     * get the cluster node information.
     *
     * @return members info, like [ip:port, ip:port, ip:port]
     */
    Set<String> getMembers();
    
    /**
     * Add configuration content.
     *
     * @param key   config key
     * @param value config value
     */
    void setVal(String key, String value);
    
    /**
     * get configuration content by key.
     *
     * @param key config key
     * @return config value
     */
    String getVal(String key);
    
    /**
     * get configuration content by key, if not found, use default-val.
     *
     * @param key        config key
     * @param defaultVal default value
     * @return config value
     */
    String getValOfDefault(String key, String defaultVal);
    
}
