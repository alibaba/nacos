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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.nacos.config.server.utils.AppNameUtils;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;

/**
 * logback test
 * @author Nacos
 *
 */
public class LogbackInitTest {

    private static final Logger logger = LoggerFactory.getLogger(LogbackInitTest.class);
       
    
    
    public static void main(String[] args) throws Exception {
        AppNameUtils.class.getClassLoader();
        String classpath = AppNameUtils.class.getResource("/").getPath();
        System.out.println("The classpath is " + classpath);
        
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        lc.reset();

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(lc);
        configurator.doConfigure(LogbackInitTest.class.getResource("logback-jiuren.xml"));
        
        for (;;) {
            logger.info("hello");
            System.out.println(getLevel(logger));
            Thread.sleep(1000L);
        }
    }
    
    
    static String getLevel(Logger logger) {
        if (logger.isDebugEnabled()) {
            return "debug";
        } else if (logger.isInfoEnabled()) {
            return "info";
        } else if (logger.isWarnEnabled()) {
            return "warn";
        } else if (logger.isErrorEnabled()) {
            return "error";
        } else {
            return "unknown";
        }
    }
}
