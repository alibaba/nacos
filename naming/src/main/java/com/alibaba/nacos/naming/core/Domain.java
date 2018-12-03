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
package com.alibaba.nacos.naming.core;

import java.util.List;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public interface Domain {
    /**
     * Get name of domain
     *
     * @return Name of domain
     */
    String getName();

    /**
     * Set name of domain
     *
     * @param name Domain name
     */
    void setName(String name);

    /**
     * Get token of domain
     *
     * @return Token of domain
     */
    String getToken();

    /**
     * Set token of domain
     *
     * @param token Domain token
     */
    void setToken(String token);

    /**
     * Get domain owners
     *
     * @return Domain owners
     */
    List<String> getOwners();

    /**
     * Set domain owners
     *
     * @param owners Domain owners
     */
    void setOwners(List<String> owners);

    /**
     * Initiation of domain
     */
    void init();

    /**
     * Domain destruction
     *
     * @throws Exception
     */
    void destroy() throws Exception;

    /**
     * Get whole list IP of domain
     *
     * @return Whole list IP of domain
     */
    List<IpAddress> allIPs();

    /**
     * Get servable IP list of domain.
     *
     * @param clientIP Request IP of client
     * @return Servable IP list of domain.
     */
    List<IpAddress> srvIPs(String clientIP);

    /**
     * get JSON serialization of domain
     *
     * @return JSON representation of domain
     */
    String toJSON();

    /**
     * Set protect threshold of domain
     *
     * @param protectThreshold Protect threshold
     */
    void setProtectThreshold(float protectThreshold);

    /**
     * Get protect threshold of domain
     *
     * @return Protect threshold of domain
     */
    float getProtectThreshold();

    /**
     * Replace domain using properties of 'dom'
     *
     * @param dom New domain
     */
    void update(Domain dom);

    /**
     * Get checksum of domain
     *
     * @return Checksum of domain
     */
    String getChecksum();

    /**
     * Refresh checksum of domain
     */
    void recalculateChecksum();
}
