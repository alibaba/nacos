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

package com.alibaba.nacos.naming.core.v2.client;

import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;

import java.util.Collection;

/**
 * Nacos naming client.
 *
 * <p>The abstract concept of the client stored by on the server of Nacos naming module. It is used to store which
 * services the client has published and subscribed.
 *
 * @author xiweng.yy
 */
public interface Client {
    
    /**
     * Get the unique id of current client.
     *
     * @return id of client
     */
    String getClientId();
    
    /**
     * Whether is ephemeral of current client.
     *
     * @return true if client is ephemeral, otherwise false
     */
    boolean isEphemeral();
    
    /**
     * Set the last time for updating current client as current time.
     */
    void setLastUpdatedTime();
    
    /**
     * Get the last time for updating current client.
     *
     * @return last time for updating
     */
    long getLastUpdatedTime();
    
    /**
     * Add a new instance for service for current client.
     *
     * @param service             publish service
     * @param instancePublishInfo instance
     * @return true if add successfully, otherwise false
     */
    boolean addServiceInstance(Service service, InstancePublishInfo instancePublishInfo);
    
    /**
     * Remove service instance from client.
     *
     * @param service service of instance
     * @return instance info if exist, otherwise {@code null}
     */
    InstancePublishInfo removeServiceInstance(Service service);
    
    /**
     * Get instance info of service from client.
     *
     * @param service service of instance
     * @return instance info
     */
    InstancePublishInfo getInstancePublishInfo(Service service);
    
    /**
     * Get all published service of current client.
     *
     * @return published services
     */
    Collection<Service> getAllPublishedService();
    
    /**
     * Add a new subscriber for target service.
     *
     * @param service    subscribe service
     * @param subscriber subscriber
     * @return true if add successfully, otherwise false
     */
    boolean addServiceSubscriber(Service service, Subscriber subscriber);
    
    /**
     * Remove subscriber for service.
     *
     * @param service service of subscriber
     * @return true if remove successfully, otherwise false
     */
    boolean removeServiceSubscriber(Service service);
    
    /**
     * Get subscriber of service from client.
     *
     * @param service service of subscriber
     * @return subscriber
     */
    Subscriber getSubscriber(Service service);
    
    /**
     * Get all subscribe service of current client.
     *
     * @return subscribe services
     */
    Collection<Service> getAllSubscribeService();
    
    /**
     * Generate sync data.
     *
     * @return sync data
     */
    ClientSyncData generateSyncData();
    
    /**
     * Whether current client is expired.
     *
     * @param currentTime unified current timestamp
     * @return true if client has expired, otherwise false
     */
    boolean isExpire(long currentTime);
    
    /**
     * Release current client and release resources if neccessary.
     */
    void release();
}
