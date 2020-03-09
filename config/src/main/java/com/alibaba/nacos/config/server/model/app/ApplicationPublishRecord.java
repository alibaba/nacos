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
package com.alibaba.nacos.config.server.model.app;

import java.io.Serializable;

/**
 * Application Publish Record
 *
 * @author Nacos
 */
public class ApplicationPublishRecord implements Serializable {

    private static final long serialVersionUID = -6235881685279344468L;
    private String appName;
    private GroupKey configInfo;

    public ApplicationPublishRecord(String appName, String dataId, String groupId) {
        this.appName = appName;
        this.configInfo = new GroupKey(dataId, groupId);
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public GroupKey getConfigInfo() {
        return configInfo;
    }

    public void setConfigInfo(GroupKey configInfo) {
        this.configInfo = configInfo;
    }

}
