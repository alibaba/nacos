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

    public static <T extends Object> RestResult<T> buildSuccessResult(){
        return buildResult(ResultCodeEnum.SUCCESS, null);
    }
}
