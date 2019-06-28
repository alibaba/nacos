package com.alibaba.nacos.config.server.result.core;

/**
 * @author klw
 * @ClassName: IResultCode
 * @Description: result code enum needs to be implemented this interface
 * @date 2019/6/28 14:44
 */
public interface IResultCode {

    /**
     * @author klw
     * @Description: get the result code
     * @Date 2019/6/28 14:56
     * @Param []
     * @return java.lang.String
     */
    int getCode();

    /**
     * @author klw
     * @Description: get the result code's message
     * @Date 2019/6/28 14:56
     * @Param []
     * @return java.lang.String
     */
    String getCodeMsg();
}
