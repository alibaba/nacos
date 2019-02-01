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
/*
 * Copyright 2014 Alibaba.com All right reserved. This software is the
 * confidential and proprietary information of Alibaba.com ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with Alibaba.com.
 */
package com.alibaba.nacos.client.logger.support;

/**
 * 兼容老的ErrorLog，后续请使用{@link LoggerHelper}
 *
 * @author zhuyong 2014年7月1日 上午11:41:22
 */
public class ErrorLog {

    public static String buildErrorMsg(String msg, String errorCode, String errorType) {
        return LoggerHelper.getErrorCodeStr(null, errorCode, errorType, msg);
    }
}
