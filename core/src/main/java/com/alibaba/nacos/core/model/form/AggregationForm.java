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

/**
 * Nacos HTTP Aggregatable API Form.
 *
 * @author xiweng.yy
 */
public class AggregationForm implements NacosForm {
    
    private static final long serialVersionUID = 3585575371677025046L;
    
    private boolean aggregation = Boolean.TRUE;
    
    @Override
    public void validate() throws NacosApiException {
    }
    
    public boolean isAggregation() {
        return aggregation;
    }
    
    public void setAggregation(boolean aggregation) {
        this.aggregation = aggregation;
    }
}
