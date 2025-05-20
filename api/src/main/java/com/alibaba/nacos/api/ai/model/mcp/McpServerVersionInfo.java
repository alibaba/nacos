/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.mcp;

import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;

import java.util.List;

/**
 * McpServerVersionInfo.
 * @author xinluo
 */
@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName", "checkstyle:SummaryJavadoc"})
public class McpServerVersionInfo extends McpServerBasicInfo {

    private String latestPublishedVersion;

    private List<ServerVersionDetail> versionDetails;

    public String getLatestPublishedVersion() {
        return latestPublishedVersion;
    }

    public void setLatestPublishedVersion(String latestPublishedVersion) {
        this.latestPublishedVersion = latestPublishedVersion;
    }

    public List<ServerVersionDetail> getVersionDetails() {
        return versionDetails;
    }

    public void setVersions(List<ServerVersionDetail> versionDetails) {
        this.versionDetails = versionDetails;
    }
}
