/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.config.remote.request;

/**
 * request of listening a batch of configs.
 *
 * @author liuzunfei
 * @version $Id: ConfigBatchListenRequest.java, v 0.1 2020年07月27日 7:46 PM liuzunfei Exp $
 */
public class ConfigBatchListenRequest extends ConfigCommonRequest {
    
    private static final String Y = "Y";
    
    private static final String N = "N";
    
    /**
     * listen or remove listen.
     */
    private String listen;
    
    /**
     * batch operation config value string.
     */
    private String listeningConfigs;
    
    @Override
    public String getType() {
        return ConfigRequestTypeConstants.BATCH_CHANGE_LISTEN_CONFIG;
    }
    
    /**
     * build batch listen request.
     *
     * @param listeningConfigs configstring of listening
     * @return
     */
    public static ConfigBatchListenRequest buildListenRequest(String listeningConfigs) {
        ConfigBatchListenRequest request = new ConfigBatchListenRequest();
        request.setListeningConfigs(listeningConfigs);
        request.setListen(Y);
        return request;
    }
    
    /**
     * build batch listen request.
     *
     * @param listeningConfigs configstring of cancel listening
     * @return
     */
    public static ConfigBatchListenRequest buildRemoveListenRequest(String listeningConfigs) {
        ConfigBatchListenRequest request = new ConfigBatchListenRequest();
        request.setListeningConfigs(listeningConfigs);
        request.setListen(N);
        return request;
    }
    
    /**
     * Getter method for property <tt>listeningConfigs</tt>.
     *
     * @return property value of listeningConfigs
     */
    public String getListeningConfigs() {
        return listeningConfigs;
    }
    
    /**
     * Setter method for property <tt>listeningConfigs</tt>.
     *
     * @param listeningConfigs value to be assigned to property listeningConfigs
     */
    public void setListeningConfigs(String listeningConfigs) {
        this.listeningConfigs = listeningConfigs;
    }
    
    /**
     * if it is a listen config request.
     *
     * @return if is a listen request.
     */
    public boolean isListenConfig() {
        return Y.equalsIgnoreCase(this.listen);
    }
    
    /**
     * Getter method for property <tt>listen</tt>.
     *
     * @return property value of listen
     */
    public String getListen() {
        return listen;
    }
    
    /**
     * Setter method for property <tt>listen</tt>.
     *
     * @param listen value to be assigned to property listen
     */
    public void setListen(String listen) {
        this.listen = listen;
    }
}
