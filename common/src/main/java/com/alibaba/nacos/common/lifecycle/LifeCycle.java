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
package com.alibaba.nacos.common.lifecycle;

/**
 *
 * The lifecycle interface for generic service. Classes are need to implement
 * this interface have a defined life cycle defined by the methods of this interface.
 *
 * @author zongtanghu
 */
public interface LifeCycle {

    /**
     * Starts the service.
     *
     * @throws Exception If the service fails to start.
     */
    public void start() throws Exception;

    /**
     * Stops the service.
     *
     * @throws Exception If the service fails to stop.
     */
    public void stop() throws Exception;

    /**
     * This method will return the service whether is running or not.
     *
     * @return true if the component is starting or has been started.
     */
    public boolean isRunning();

    /**
     * This method will return the service whether is started or not.
     *
     * @return true if the service has been started.
     *
     */
    public boolean isStarted();

    /**
     * This method will return the service whether is starting or not.
     *
     * @return true if the component is starting.
     *
     */
    public boolean isStarting();

    /**
     * This method will return the service whether is stopping or not.
     *
     * @return true if the service is stopping.
     */
    public boolean isStopping();

    /**
     * This method will return the service whether is stopped or not.
     *
     * @return true if the service has been stopped.
     */
    public boolean isStopped();

    /**
     * This method will return the service whether is failed or not.
     *
     * @return true if the service has failed to start or has failed to stop.
     */
    public boolean isFailed();
}
