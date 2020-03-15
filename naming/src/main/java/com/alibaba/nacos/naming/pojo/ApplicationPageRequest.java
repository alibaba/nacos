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
package com.alibaba.nacos.naming.pojo;

import com.alibaba.nacos.api.common.Constants;
import org.apache.commons.lang3.StringUtils;

import java.util.StringJoiner;

/**
 * @author kkyeer
 * @Description: Application Page Request Param
 * @Date:Created in 16:44 2-22
 * @Modified By:
 */
public class ApplicationPageRequest {
    /**
     * page number
     */
    private Integer pageNo = 1;
    /**
     * size per page
     */
    private Integer pageSize = 10;
    /**
     * namespace id
     */
    private String namespaceId = Constants.DEFAULT_NAMESPACE_ID;
    /**
     * ip of application
     */
    private String applicationIp;
    /**
     * port of application
     */
    private Integer applicationPort;

    public Integer getPageNo() {
        return pageNo;
    }

    public ApplicationPageRequest setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
        return this;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public ApplicationPageRequest setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    public ApplicationPageRequest setNamespaceId(String namespaceId) {
        if (StringUtils.isBlank(namespaceId)) {
            this.namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        }else {
            this.namespaceId = namespaceId;
        }
        return this;
    }

    public String getApplicationIp() {
        return applicationIp;
    }

    public ApplicationPageRequest setApplicationIp(String applicationIp) {
        this.applicationIp = applicationIp;
        return this;
    }

    public Integer getApplicationPort() {
        return applicationPort;
    }

    public ApplicationPageRequest setApplicationPort(Integer applicationPort) {
        this.applicationPort = applicationPort;
        return this;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ApplicationPageRequest.class.getSimpleName() + "[", "]")
            .add("pageNo=" + pageNo)
            .add("pageSize=" + pageSize)
            .add("namespaceId='" + namespaceId + "'")
            .add("applicationIp='" + applicationIp + "'")
            .add("applicationPort=" + applicationPort)
            .toString();
    }
}
