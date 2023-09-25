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

package com.alibaba.nacos.common.paramcheck;

import java.util.List;

/**
 * The type Abstract param checker.
 *
 * @author zhuoguang
 */
public abstract class AbstractParamChecker {
    
    protected ParamCheckRule paramCheckRule;
    
    public AbstractParamChecker() {
        initParamCheckRule();
    }
    
    /**
     * Gets checker type.
     *
     * @return the checker type
     */
    public abstract String getCheckerType();
    
    /**
     * Check param info list param check response.
     *
     * @param paramInfos the param infos
     * @return the param check response
     */
    public abstract ParamCheckResponse checkParamInfoList(List<ParamInfo> paramInfos);
    
    /**
     * Init param check rule.
     */
    public abstract void initParamCheckRule();
}
