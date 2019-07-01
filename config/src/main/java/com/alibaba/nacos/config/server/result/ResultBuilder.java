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
package com.alibaba.nacos.config.server.result;

import com.alibaba.nacos.config.server.model.RestResult;
import com.alibaba.nacos.config.server.result.code.ResultCodeEnum;
import com.alibaba.nacos.config.server.result.core.IResultCode;
import org.springframework.util.Assert;

/**
 * @author klw
 * @ClassName: ResultBuilder
 * @Description: util for generating com.alibaba.nacos.config.server.model.RestResult
 * @date 2019/6/28 14:47
 */
public class ResultBuilder {

    public static <T extends Object> RestResult<T> buildResult(IResultCode resultCode, T resultData){
        Assert.notNull(resultCode, "the resultCode can not be null");
        RestResult<T> rr = new RestResult<>(resultCode.getCode(), resultCode.getCodeMsg(), resultData);
        return rr;
    }

    public static <T extends Object> RestResult<T> buildSuccessResult(T resultData){
        return buildResult(ResultCodeEnum.SUCCESS, resultData);
    }

    public static <T extends Object> RestResult<T> buildSuccessResult(String successMsg, T resultData){
        RestResult<T> rr = buildResult(ResultCodeEnum.SUCCESS, resultData);
        rr.setMessage(successMsg);
        return rr;
    }

    public static <T extends Object> RestResult<T> buildSuccessResult(){
        return buildResult(ResultCodeEnum.SUCCESS, null);
    }

    public static <T extends Object> RestResult<T> buildSuccessResult(String successMsg){
        RestResult<T> rr = buildResult(ResultCodeEnum.SUCCESS, null);
        rr.setMessage(successMsg);
        return rr;
    }
}
