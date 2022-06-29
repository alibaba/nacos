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

package com.alibaba.nacos.client.env;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class EnvironmentSearchTest {
    
    private static final Properties JVM_ARGS = new Properties();
    
    private static final Map<String, String> SYS_ENV = new HashMap<>();
    
    @BeforeClass
    public static void init() throws NoSuchFieldException, IllegalAccessException {
        injectJvmArgs(JVM_ARGS);
        injectSystemEnv(SYS_ENV);
        SYS_ENV.put("nacos.home", "home.sys.env");
        JVM_ARGS.put("nacos.home", "home.jvm.args");
        UserCustomizableEnvironment.getInstance().setProperty("nacos.home", "home.user");
    
    }
    
    @Test
    public void testBuild() {
        
        final EnvironmentSearch environmentSearch = EnvironmentSearch.Builder.envs(UserCustomizableEnvironment.getInstance(),
                        JvmArgumentsEnvironment.getInstance(), SystemEnvironment.getInstance())
                .order(EnvType.SYSTEM_ENV, EnvType.JVM_ARGS, EnvType.USER_CUSTOMIZABLE).build();
    
        final String value = environmentSearch.search(environment -> environment.getProperty("nacos.home"));
    
        Assert.assertEquals("home.sys.env", value);
    
    }
    
    @Test
    public void testFirst() {
        final EnvironmentSearch environmentSearch = EnvironmentSearch.Builder.envs(UserCustomizableEnvironment.getInstance(),
                        JvmArgumentsEnvironment.getInstance(), SystemEnvironment.getInstance())
                .order(EnvType.JVM_ARGS, EnvType.USER_CUSTOMIZABLE).first();
        
        final String value = environmentSearch.search(environment -> environment.getProperty("nacos.home"));
        Assert.assertEquals("home.jvm.args", value);
    }
    
    @Test
    public void testFirstWithoutOrder() {
    
        final EnvironmentSearch environmentSearch = EnvironmentSearch.Builder.envs(UserCustomizableEnvironment.getInstance(),
                        JvmArgumentsEnvironment.getInstance(), SystemEnvironment.getInstance()).first();
        final String value = environmentSearch.search(environment -> environment.getProperty("nacos.home"));
    
        Assert.assertEquals("home.user", value);
    }
    
    @Test
    public void testLast() {
        final EnvironmentSearch environmentSearch = EnvironmentSearch.Builder.envs(UserCustomizableEnvironment.getInstance(),
                        JvmArgumentsEnvironment.getInstance(), SystemEnvironment.getInstance())
                .order(EnvType.JVM_ARGS, EnvType.SYSTEM_ENV).last();
    
        final String value = environmentSearch.search(environment -> environment.getProperty("nacos.home"));
        Assert.assertEquals("home.user", value);
        
    }
    
    @Test
    public void testLastWithoutOrder() {
    
        final EnvironmentSearch environmentSearch = EnvironmentSearch.Builder.envs(UserCustomizableEnvironment.getInstance(),
                JvmArgumentsEnvironment.getInstance(), SystemEnvironment.getInstance()).last();
        final String value = environmentSearch.search(environment -> environment.getProperty("nacos.home"));
    
        Assert.assertEquals("home.user", value);
    }
    
    @Test
    public void testSearchWithDefaultValue() {
        final EnvironmentSearch environmentSearch = EnvironmentSearch.Builder.envs(UserCustomizableEnvironment.getInstance(),
                        JvmArgumentsEnvironment.getInstance(), SystemEnvironment.getInstance())
                .order(EnvType.SYSTEM_ENV, EnvType.JVM_ARGS, EnvType.USER_CUSTOMIZABLE).build();
    
        final String value = environmentSearch.search(environment -> environment.getProperty("nacos.user.timeout"),
                "1000");
        
        Assert.assertEquals("1000", value);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSearchNoneEnvs() {
        final EnvironmentSearch environmentSearch = EnvironmentSearch.Formatter
                .of("system_env,user_customizable,jvm_args")
                .parse();
    }
    
    @Test
    public void testSearchFormatterWithoutOpt() {
        final EnvironmentSearch environmentSearch = EnvironmentSearch.Formatter.of("system_env,user_customizable,jvm_args",
                UserCustomizableEnvironment.getInstance(), JvmArgumentsEnvironment.getInstance(),
                SystemEnvironment.getInstance()).parse();
    
        final String value = environmentSearch.search(environment -> environment.getProperty("nacos.home"));
    
        Assert.assertEquals("home.sys.env", value);
    }
    
    @Test
    public void testSearchFormatterWithOnlyOpt() {
        final EnvironmentSearch environmentSearch = EnvironmentSearch.Formatter.of("jvm_args,user_customizable@build",
                UserCustomizableEnvironment.getInstance(), JvmArgumentsEnvironment.getInstance(),
                SystemEnvironment.getInstance()).parse();
        
        final String value = environmentSearch.search(environment -> environment.getProperty("nacos.home"));
        
        Assert.assertEquals("home.jvm.args", value);
    }
    
    @Test
    public void testSearchFormatterWithErrorOpt() {
        final EnvironmentSearch environmentSearch = EnvironmentSearch.Formatter.of("user_customizable,jvm_args,system_env@test",
                UserCustomizableEnvironment.getInstance(), JvmArgumentsEnvironment.getInstance(),
                SystemEnvironment.getInstance()).parse();
        
        final String value = environmentSearch.search(environment -> environment.getProperty("nacos.home"));
        
        Assert.assertEquals("home.user", value);
    }
    
    @Test
    public void testSearchFormatterWithFirstOpt() {
    
        final EnvironmentSearch environmentSearch = EnvironmentSearch.Formatter.of("jvm_args,system_env@first",
                UserCustomizableEnvironment.getInstance(), JvmArgumentsEnvironment.getInstance(),
                SystemEnvironment.getInstance()).parse();
        
        final String value = environmentSearch.search(environment -> environment.getProperty("nacos.home"));
    
        Assert.assertEquals("home.jvm.args", value);
    }
    
    @Test
    public void testSearchFormatterWithLastOpt() {
        
        final EnvironmentSearch environmentSearch = EnvironmentSearch.Formatter.of("jvm_args,system_env@last",
                UserCustomizableEnvironment.getInstance(), JvmArgumentsEnvironment.getInstance(),
                SystemEnvironment.getInstance()).parse();
        
        final String value = environmentSearch.search(environment -> environment.getProperty("nacos.home"));
        
        Assert.assertEquals("home.user", value);
    }
    
    @AfterClass
    public static void teardown() throws NoSuchFieldException, IllegalAccessException {
        final Properties properties = System.getProperties();
        injectJvmArgs(properties);
    
        final Map<String, String> env = System.getenv();
    
        injectSystemEnv(env);
    }
    
    private static void injectJvmArgs(Object o) throws NoSuchFieldException, IllegalAccessException {
    
        final Class<JvmArgumentsEnvironment> jvmArgumentsEnvironmentClass = JvmArgumentsEnvironment.class;
        final Field envsField = jvmArgumentsEnvironmentClass.getDeclaredField("envs");
        envsField.setAccessible(true);
    
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(envsField, envsField.getModifiers() & ~Modifier.FINAL);
    
        envsField.set(JvmArgumentsEnvironment.getInstance(), o);
    }
    
    private static void injectSystemEnv(Object o) throws NoSuchFieldException, IllegalAccessException {
        final Class<SystemEnvironment> systemEnvironmentClass = SystemEnvironment.class;
        final Field envsField = systemEnvironmentClass.getDeclaredField("envs");
        envsField.setAccessible(true);
    
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(envsField, envsField.getModifiers() & ~Modifier.FINAL);
    
        envsField.set(SystemEnvironment.getInstance(), o);
    }

}
