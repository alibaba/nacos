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
package com.alibaba.nacos.client.logger.option;

import java.util.List;
import java.util.Map;

import com.alibaba.nacos.client.logger.Level;
import com.alibaba.nacos.client.logger.Logger;

/**
 * <pre>
 * 激活Logger的选项，包括：
 * Appender/Layout
 * Level
 * Additivity
 * Aysnc
 * 请参考具体的实现逻辑
 * </pre>
 *
 * @author zhuyong 2014年3月20日 上午10:20:51
 */
public interface ActivateOption {

    /**
     * 设置ConsoleAppender，生产环境慎用
     *
     * @param target   System.out or System.err
     * @param encoding 编码
     */
    void activateConsoleAppender(String target, String encoding);

    /**
     * 设置FileAppender，日志按天回滚
     *
     * @param productName 中间件产品名，如hsf, tddl
     * @param file        日志文件名，如hsf.log，支持子目录，如client/hsf.log
     * @param encoding    编码
     */
    void activateAppender(String productName, String file, String encoding);

    /**
     * 设置AsyncAppender，内嵌DailyRollingFileAppender，日志按天回滚，参考 {@link ActivateOption#activateAsync(int, int)}
     *
     * @param productName 中间件产品名，如hsf, tddl
     * @param file        日志文件名，如hsf.log，支持子目录，如client/hsf.log
     * @param encoding    编码
     */
    @Deprecated
    void activateAsyncAppender(String productName, String file, String encoding);

    /**
     * 设置AsyncAppender，内嵌DailyRollingFileAppender，日志按天回滚，参考 {@link ActivateOption#activateAsync(int, int)}
     *
     * @param productName         中间件产品名，如hsf, tddl
     * @param file                日志文件名，如hsf.log，支持子目录，如client/hsf.log
     * @param encoding            编码
     * @param queueSize           等待队列大小
     * @param discardingThreshold discardingThreshold，该参数仅对logback实现有效，log4j和log4j2无效
     */
    @Deprecated
    void activateAsyncAppender(String productName, String file, String encoding, int queueSize,
                               int discardingThreshold);

    /**
     * 设置按天和文件大小回滚
     *
     * @param productName 中间件产品名，如hsf, tddl
     * @param file        日志文件名，如hsf.log，支持子目录，如client/hsf.log
     * @param encoding    编码
     * @param size        文件大小，如300MB，支持KB，MB，GB，该参数对log4j实现不生效，log4j2和logback有效
     */
    void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size);

    /**
     * <pre>
     * 设置按日期格式和文件大小回滚
     * 说明：Log4j 对日期格式不生效，只有按大小回滚，同时不支持备份文件，即达到文件大小直接截断，如果需要备份文件，请参考带 maxBackupIndex 参数的方法
     * </pre>
     *
     * @param productName 中间件产品名，如hsf, tddl
     * @param file        日志文件名，如hsf.log，支持子目录，如client/hsf.log
     * @param encoding    编码
     * @param size        文件大小，如300MB，支持KB，MB，GB
     * @param datePattern 日期格式，如yyyy-MM-dd 或 yyyy-MM，请自行保证格式正确，该参数对log4j实现不生效，log4j2和logback有效
     */
    void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size,
                                                String datePattern);

    /**
     * <pre>
     * 设置按日期格式、文件大小、最大备份文件数回滚
     * 说明：
     * 1、Log4j 对日期格式不生效，只有按大小、备份文件数回滚，备份文件数 maxBackupIndex 参数必须是 >= 0 的整数，为0时表示直接截断，不备份
     * 2、备份日志格式说明：
     *     Log4j：notify.log.1, notify.log.2，即备份文件以 .1 .2结尾，序号从1开始
     *     Logback: notify.log.2014-09-19.0， notify.log.2014-09-19.1，即中间会带日期格式，同时序号从0开始
     * </pre>
     *
     * @param productName    中间件产品名，如hsf, tddl
     * @param file           日志文件名，如hsf.log，支持子目录，如client/hsf.log
     * @param encoding       编码
     * @param size           文件大小，如300MB，支持KB，MB，GB
     * @param datePattern    日期格式，如yyyy-MM-dd 或 yyyy-MM，请自行保证格式正确，该参数对log4j实现不生效，log4j2和logback有效
     * @param maxBackupIndex 最大备份文件数，如10（对于 Logback，则是保留10天的文件，但是这10天内的文件则会按大小回滚）
     */
    void activateAppenderWithTimeAndSizeRolling(String productName, String file, String encoding, String size,
                                                String datePattern, int maxBackupIndex);

    /**
     * <pre>
     * 设置按文件大小、最大备份文件数回滚
     * 说明：
     * 1、Log4j 备份文件数 maxBackupIndex 参数必须是 >= 0 的整数，为0时表示直接截断，不备份
     * 2、备份日志格式说明：
     *     Log4j：notify.log.1, notify.log.2，即备份文件以 .1 .2结尾，序号从1开始
     *     Logback: notify.log.1， notify.log.1
     * </pre>
     *
     * @param productName    中间件产品名，如hsf, tddl
     * @param file           日志文件名，如hsf.log，支持子目录，如client/hsf.log
     * @param encoding       编码
     * @param size           文件大小，如300MB，支持KB，MB，GB
     * @param maxBackupIndex 最大备份文件数，如10
     */
    void activateAppenderWithSizeRolling(String productName, String file, String encoding, String size,
                                         int maxBackupIndex);

    /**
     * 将当前logger对象的appender设置为异步Appender 注意：此logger需要提前进行Appender的初始化
     *
     * @param queueSize           等待队列大小
     * @param discardingThreshold discardingThreshold，该参数仅对logback实现有效，log4j和log4j2无效
     * @since 0.2.2
     */
    void activateAsync(int queueSize, int discardingThreshold);

    /**
     * 将当前logger对象的appender设置为异步Appender 注意：此logger需要提前进行Appender的初始化
     *
     * @param args AsyncAppender配置参数，请自行保证参数的正确性，要求每个Object[]有3个元素，第一个为set方法名，第二个为方法类型数组，第三个为对应的参数值，如 args.add(new
     *             Object[] { "setBufferSize", new Class<?>[] { int.class }, queueSize });
     * @since 0.2.3
     */
    void activateAsync(List<Object[]> args);

    /**
     * 使用logger对象的appender来初始化当前logger
     *
     * @param logger
     */
    void activateAppender(Logger logger);

    /**
     * 设置日志级别
     *
     * @param level 日志级别
     * @see Level
     */
    void setLevel(Level level);

    /**
     * 获取日志级别
     *
     * @return level
     */
    Level getLevel();

    /**
     * 设置日志是否Attach到Parent
     *
     * @param additivity true or false
     */
    void setAdditivity(boolean additivity);

    /**
     * 获取所属的产品名
     *
     * @return 所属的产品名
     */
    String getProductName();
}
