/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.namespace.model.form;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.Objects;

/**
 * NamespaceForm.
 * @author dongyafei
 * @date 2022/8/16
 */
public class NamespaceForm implements Serializable {
    
    private static final long serialVersionUID = -1078976569495343487L;
    
    private String namespaceId;
    
    private String namespaceName;
    
    private String namespaceDesc;
    
    public NamespaceForm() {
    }
    
    public NamespaceForm(String namespaceId, String namespaceName, String namespaceDesc) {
        this.namespaceId = namespaceId;
        this.namespaceName = namespaceName;
        this.namespaceDesc = namespaceDesc;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getNamespaceName() {
        return namespaceName;
    }
    
    public void setNamespaceName(String namespaceName) {
        this.namespaceName = namespaceName;
    }
    
    public String getNamespaceDesc() {
        return namespaceDesc;
    }
    
    public void setNamespaceDesc(String namespaceDesc) {
        this.namespaceDesc = namespaceDesc;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NamespaceForm that = (NamespaceForm) o;
        return Objects.equals(namespaceId, that.namespaceId) && Objects.equals(namespaceName, that.namespaceName)
                && Objects.equals(namespaceDesc, that.namespaceDesc);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespaceId, namespaceName, namespaceDesc);
    }
    
    @Override
    public String toString() {
        return "NamespaceVo{" + "namespaceId='" + namespaceId + '\'' + ", namespaceName='" + namespaceName + '\''
                + ", namespaceDesc='" + namespaceDesc + '\'' + '}';
    }
    
    /**
     * check required param.
     * @throws NacosException NacosException
     */
    public void validate() throws NacosException {
        if (null == namespaceId) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING, "required parameter 'namespaceId' is missing");
        }
        if (null == namespaceName) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING, "required parameter 'namespaceName' is missing");
        }
    }
}
