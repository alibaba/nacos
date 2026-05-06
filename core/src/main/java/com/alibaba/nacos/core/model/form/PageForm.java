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

package com.alibaba.nacos.core.model.form;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Nacos HTTP page API form.
 *
 * @author xiweng.yy
 */
public class PageForm implements NacosForm {
    
    private static final long serialVersionUID = -8912131925234465033L;
    
    private int pageNo = 1;
    
    private int pageSize = 100;
    
    @Override
    public void validate() throws NacosApiException {
        if (pageNo < 1) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                    String.format("Required parameter 'pageNo' should be positive integer, current is %d", pageNo));
        }
        if (pageSize < 1) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                    String.format("Required parameter 'pageSize' should be positive integer, current is %d", pageSize));
        }
    }
    
    public int getPageNo() {
        return pageNo;
    }
    
    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
