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

import com.alibaba.nacos.client.logger.Level;
import com.alibaba.nacos.client.logger.support.LogLog;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * AbstractActiveOption
 *
 * @author Nacos
 */
public abstract class AbstractActiveOption implements ActivateOption {

    protected String productName;
    protected Level level;

    @Override
    public String getProductName() {
        return productName;
    }

    @Override
    public Level getLevel() {
        return level;
    }

    protected void setProductName(String productName) {
        if (this.productName == null && productName != null) {
            this.productName = productName;
        }
    }

    public static void invokeMethod(Object object, List<Object[]> args) {
        if (args != null && object != null) {
            for (Object[] arg : args) {
                if (arg != null && arg.length == 3) {
                    try {
                        Method m = object.getClass().getMethod((String)arg[0], (Class<?>[])arg[1]);
                        m.invoke(object, arg[2]);
                    } catch (NoSuchMethodException e) {
                        LogLog.info("Can't find method for " + object.getClass() + " " + arg[0] + " " + arg[2]);
                    } catch (IllegalAccessException e) {
                        LogLog.info("Can't invoke method for " + object.getClass() + " " + arg[0] + " " + arg[2]);
                    } catch (InvocationTargetException e) {
                        LogLog.info("Can't invoke method for " + object.getClass() + " " + arg[0] + " " + arg[2]);
                    } catch (Throwable t) {
                        LogLog.info("Can't invoke method for " + object.getClass() + " " + arg[0] + " " + arg[2]);
                    }
                }
            }
        }
    }
}
