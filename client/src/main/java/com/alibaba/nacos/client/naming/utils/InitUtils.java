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

package com.alibaba.nacos.client.naming.utils;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.SystemPropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.utils.*;

import java.util.Properties;
import java.util.concurrent.Callable;

/**
 * @author liaochuntao
 * @author deshao
 */
public class InitUtils {

    /**
     * Add a difference to the name naming. This method simply initializes the namespace for Naming.
     * Config initialization is not the same, so it cannot be reused directly.
     *
     * @param properties
     * @return
     */
    public static String initNamespaceForNaming(Properties properties) {
        String tmpNamespace = null;


        String isUseCloudNamespaceParsing =
            properties.getProperty(PropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING,
                System.getProperty(SystemPropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING,
                    String.valueOf(Constants.DEFAULT_USE_CLOUD_NAMESPACE_PARSING)));

        if (Boolean.valueOf(isUseCloudNamespaceParsing)) {

            tmpNamespace = TenantUtil.getUserTenantForAns();
            tmpNamespace = TemplateUtils.stringEmptyAndThenExecute(tmpNamespace, new Callable<String>() {
                @Override
                public String call() {
                    String namespace = System.getProperty(SystemPropertyKeyConst.ANS_NAMESPACE);
                    LogUtils.NAMING_LOGGER.info("initializer namespace from System Property :" + namespace);
                    return namespace;
                }
            });

            tmpNamespace = TemplateUtils.stringEmptyAndThenExecute(tmpNamespace, new Callable<String>() {
                @Override
                public String call() {
                    String namespace = System.getenv(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_NAMESPACE);
                    LogUtils.NAMING_LOGGER.info("initializer namespace from System Environment :" + namespace);
                    return namespace;
                }
            });
        }

        tmpNamespace = TemplateUtils.stringEmptyAndThenExecute(tmpNamespace, new Callable<String>() {
            @Override
            public String call() {
                String namespace = System.getProperty(PropertyKeyConst.NAMESPACE);
                LogUtils.NAMING_LOGGER.info("initializer namespace from System Property :" + namespace);
                return namespace;
            }
        });

        if (StringUtils.isEmpty(tmpNamespace) && properties != null) {
            tmpNamespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
        }

        tmpNamespace = TemplateUtils.stringEmptyAndThenExecute(tmpNamespace, new Callable<String>() {
            @Override
            public String call() {
                return UtilAndComs.DEFAULT_NAMESPACE_ID;
            }
        });
        return tmpNamespace;
    }

    public static void initWebRootContext() {
        // support the web context with ali-yun if the app deploy by EDAS
        final String webContext = System.getProperty(SystemPropertyKeyConst.NAMING_WEB_CONTEXT);
        TemplateUtils.stringNotEmptyAndThenExecute(webContext, new Runnable() {
            @Override
            public void run() {
                UtilAndComs.WEB_CONTEXT = webContext.indexOf("/") > -1 ? webContext
                    : "/" + webContext;

                UtilAndComs.NACOS_URL_BASE = UtilAndComs.WEB_CONTEXT + "/v1/ns";
                UtilAndComs.NACOS_URL_INSTANCE = UtilAndComs.NACOS_URL_BASE + "/instance";
            }
        });
    }

    public static String initEndpoint(final Properties properties) {
        if (properties == null) {

            return "";
        }
        // Whether to enable domain name resolution rules
        String isUseEndpointRuleParsing =
            properties.getProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                System.getProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                    String.valueOf(ParamUtil.USE_ENDPOINT_PARSING_RULE_DEFAULT_VALUE)));

        boolean isUseEndpointParsingRule = Boolean.valueOf(isUseEndpointRuleParsing);
        String endpointUrl;
        if (isUseEndpointParsingRule) {
            // Get the set domain name information
            endpointUrl = ParamUtil.parsingEndpointRule(properties.getProperty(PropertyKeyConst.ENDPOINT));
            if (StringUtils.isBlank(endpointUrl)) {
                return "";
            }
        } else {
            endpointUrl = properties.getProperty(PropertyKeyConst.ENDPOINT);
        }

        if (StringUtils.isBlank(endpointUrl)) {
            return "";
        }

        String endpointPort = TemplateUtils.stringEmptyAndThenExecute(System.getenv(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_PORT), new Callable<String>() {
            @Override
            public String call() {

                return properties.getProperty(PropertyKeyConst.ENDPOINT_PORT);
            }
        });

        endpointPort = TemplateUtils.stringEmptyAndThenExecute(endpointPort, new Callable<String>() {
            @Override
            public String call() {
                return "8080";
            }
        });

        return endpointUrl + ":" + endpointPort;
    }

}
