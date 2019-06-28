package com.alibaba.nacos.config.server.result.code;

import com.alibaba.nacos.config.server.result.core.IResultCode;

/**
 * @author klw
 * @ClassName: ResultCodeEnum
 * @Description: result code enum
 * @date 2019/6/28 14:43
 */
public enum ResultCodeEnum implements IResultCode {

    /** common code **/
    SUCCESS(200, "处理成功"),
    ERROR(500, "服务器内部错误"),

    /** config use 100001 ~ 100999 **/
    NAMESPACE_NOT_EXIST(100001, "目标 namespace 不存在"),

    METADATA_ILLEGAL(100002, "导入的元数据非法"),

    DATA_VALIDATION_FAILED(100003, "未读取到合法数据"),

    PARSING_DATA_FAILED(100004, "解析数据失败"),

    DATA_EMPTY(100005, "导入的文件数据为空"),



    ;

    private int code;

    private String msg;

    ResultCodeEnum(int code, String codeMsg){
        this.code = code;
        this.msg = codeMsg;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getCodeMsg() {
        return msg;
    }
}
