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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 
 * ServerVersionDetail.
 * 
 * @author xinluo
 */
@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName", 
        "checkstyle:SummaryJavadoc", "PMD.LowerCamelCaseVariableNamingRule"})
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerVersionDetail {

    private String version;

    private String release_date;

    private Boolean is_latest;

    public String getRelease_date() {
        return release_date;
    }

    public String getVersion() {
        return version;
    }

    public void setRelease_date(String releaseDate) {
        this.release_date = releaseDate;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setIs_latest(Boolean is_latest) {
        this.is_latest = is_latest;
    }

    public Boolean getIs_latest() {
        return is_latest;
    }
}
