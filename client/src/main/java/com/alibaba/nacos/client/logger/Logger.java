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
package com.alibaba.nacos.client.logger;

import com.alibaba.nacos.client.logger.option.ActivateOption;

/**
 * <pre>
 * 阿里中间件日志API，用于输出定制化的日志
 *
 * 定制格式如下：01 %d{yyyy-MM-dd HH:mm:ss.SSS} %p [%-5t:%c{2}] %m%n
 * 其中：
 * 01                           日志API版本，后续如果格式有变化，会修改此版本号，方便机器解析
 * d{yyyy-MM-dd HH:mm:ss.SSS}   时间，如，2014-03-19 20:55:08.501，最后面的表示毫秒
 * %p                           日志级别，如INFO,ERROR
 * [%-5t:%c{2}]                 线程名:日志名
 * %m                           日志信息
 * %n                           换行
 *
 * 关于%m，也有其中的格式要求：[Context] [STAT-INFO] [ERROR-CODE]
 * 其中：
 * Context                      打印时间时的上下文信息，如果没有，则内容为空，但'[]'这个占位符仍要输出
 * STAT-INFO                    待定
 * ERROR-CODE                   常见的错误码，帮助用户解决问题
 *
 * 在异常中，也需要输出ErrorCode及对应的TraceUrl，可以使用
 * com.alibaba.nacos.client.logger.support.LoggerHelper.getErrorCodeStr(String errorCode)来获取格式化后的串
 * </pre>
 *
 * @author zhuyong 2014年3月20日 上午9:58:27
 */
public interface Logger extends ActivateOption {

    /**
     * 输出Debug日志
     *
     * @param message 日志信息（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     */
    void debug(String message);

    /**
     * 输出Debug日志
     *
     * @param format 日志信息格式化字符串，比如 'Hi,{} {} {}'（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     * @param args   格式化串参数数组
     */
    void debug(String format, Object... args);

    /**
     * 输出Debug日志
     *
     * @param context 日志上下文信息
     * @param message 日志信息（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     */
    void debug(String context, String message);

    /**
     * 输出Debug日志
     *
     * @param context 日志上下文信息
     * @param format  日志信息格式化字符串，比如 'Hi,{} {} {}'（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     * @param args    格式化串参数数组
     */
    void debug(String context, String format, Object... args);

    /**
     * 输出Info日志
     *
     * @param message 日志信息（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     */
    void info(String message);

    /**
     * 输出Info日志
     *
     * @param format 日志信息格式化字符串，比如 'Hi,{} {} {}'（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     * @param args   格式化串参数数组
     */
    void info(String format, Object... args);

    /**
     * 输出Info日志
     *
     * @param context 日志上下文信息
     * @param message 日志信息（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     */
    void info(String context, String message);

    /**
     * 输出Info日志
     *
     * @param context 日志上下文信息
     * @param format  日志信息格式化字符串，比如 'Hi,{} {} {}'（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     * @param args    格式化串参数数组
     */
    void info(String context, String format, Object... args);

    /**
     * 输出Warn日志
     *
     * @param message 日志信息（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     */
    void warn(String message);

    /**
     * 输出Warn日志
     *
     * @param message 日志信息（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     * @param t       异常信息
     * @since 0.1.5
     */
    void warn(String message, Throwable t);

    /**
     * 输出Warn日志
     *
     * @param format 日志信息格式化字符串，比如 'Hi,{} {} {}'（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     * @param args   格式化串参数数组
     */
    void warn(String format, Object... args);

    /**
     * 输出Warn日志
     *
     * @param context 日志上下文信息
     * @param message 日志信息（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     */
    void warn(String context, String message);

    /**
     * 输出Warn日志
     *
     * @param context 日志上下文信息
     * @param format  日志信息格式化字符串，比如 'Hi,{} {} {}'（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     * @param args    格式化串参数数组
     */
    void warn(String context, String format, Object... args);

    /**
     * 输出Error日志
     *
     * @param errorCode 错误码，如HSF-0001
     * @param message   日志信息（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     */
    void error(String errorCode, String message);

    /**
     * 输出Error日志
     *
     * @param errorCode 错误码，如HSF-0001
     * @param message   日志信息（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     * @param t         异常信息
     */
    void error(String errorCode, String message, Throwable t);

    /**
     * 输出Error日志
     *
     * @param errorCode 错误码，如HSF-0001
     * @param format    日志信息格式化字符串，比如 'Hi,{} {} {}'（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     * @param objs      格式化串参数数组
     */
    void error(String errorCode, String format, Object... objs);

    /**
     * 输出Error日志
     *
     * @param context   日志上下文信息
     * @param errorCode 错误码
     * @param message   日志信息（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     */
    void error(String context, String errorCode, String message);

    /**
     * 输出Error日志
     *
     * @param context   日志上下文信息
     * @param errorCode 错误码
     * @param message   日志信息（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     * @param t         异常信息
     */
    void error(String context, String errorCode, String message, Throwable t);

    /**
     * 输出Error日志
     *
     * @param context   日志上下文信息
     * @param errorCode 错误码
     * @param format    日志信息格式化字符串，比如 'Hi,{} {} {}'（当使用ResourceBundle用于国际化日志输出时，message为对应的key, since 0.1.5）
     * @param args      格式化串参数
     */
    void error(String context, String errorCode, String format, Object... args);

    /**
     * 判断Debug级别是否开启
     *
     * @return Debug级别是否开启
     */
    boolean isDebugEnabled();

    /**
     * 判断Info级别是否开启
     *
     * @return Info级别是否开启
     */
    boolean isInfoEnabled();

    /**
     * 判断Warn级别是否开启
     *
     * @return Warn级别是否开启
     */
    boolean isWarnEnabled();

    /**
     * 判断Error级别是否开启
     *
     * @return Error级别是否开启
     */
    boolean isErrorEnabled();

    /**
     * * 获取内部日志实现对象
     *
     * @return 内部日志实现对象
     */
    Object getDelegate();
}
