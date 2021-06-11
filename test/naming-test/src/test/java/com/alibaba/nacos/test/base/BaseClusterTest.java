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

package com.alibaba.nacos.test.base;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.config.server.model.event.RaftDbErrorEvent;
import com.alibaba.nacos.config.server.service.repository.embedded.DistributedDatabaseOperateImpl;
import com.alibaba.nacos.consistency.ProtocolMetaData;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.MetadataKey;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.context.support.StandardServletEnvironment;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BaseClusterTest extends HttpClient4Test {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseClusterTest.class);
    
    protected static NamingService inaming7;
    
    protected static NamingService inaming8;
    
    protected static NamingService inaming9;
    
    protected static final NacosRestTemplate NACOS_REST_TEMPLATE = HttpClientBeanHolder.getNacosRestTemplate(LOGGER);
    
    protected static final AtomicBoolean[] FINISHED = new AtomicBoolean[] {new AtomicBoolean(false),
            new AtomicBoolean(false), new AtomicBoolean(false)};
    
    protected static Map<String, ConfigurableApplicationContext> applications = new HashMap<>();
    
    protected static String clusterInfo;
    
    static {
        System.getProperties().setProperty("nacos.core.auth.enabled", "false");
        System.getProperties().setProperty("embeddedStorage", "true");
        String ip = InetUtils.getSelfIP();
        clusterInfo = "nacos.member.list=" + ip + ":8847," + ip + ":8848," + ip + ":8849";
        
        NotifyCenter.registerSubscriber(new Subscriber<RaftDbErrorEvent>() {
            @Override
            public void onEvent(RaftDbErrorEvent event) {
                System.out.print(event.getEx());
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return RaftDbErrorEvent.class;
            }
        });
    }
    
    @BeforeClass
    public static void before() throws Exception {
        
        CountDownLatch latch = new CountDownLatch(3);
        
        Runnable runnable = () -> {
            for (int i = 0; i < 3; i++) {
                try {
                    URL runnerUrl = new File("../console/target/classes").toURI().toURL();
                    URL[] urls = new URL[] {runnerUrl};
                    URLClassLoader cl = new URLClassLoader(urls);
                    Class<?> runnerClass = cl.loadClass("com.alibaba.nacos.Nacos");
                    run(i, latch, runnerClass);
                } catch (Exception e) {
                    latch.countDown();
                }
            }
        };
        
        new Thread(runnable).start();
        
        latch.await();
        
        System.out.println("The cluster node initialization is complete");
        
        Properties setting7 = new Properties();
        String serverIp7 = "127.0.0.1:8847";
        setting7.put(PropertyKeyConst.SERVER_ADDR, serverIp7);
        setting7.put(PropertyKeyConst.USERNAME, "nacos");
        setting7.put(PropertyKeyConst.PASSWORD, "nacos");
        inaming7 = NacosFactory.createNamingService(setting7);
        
        Properties setting8 = new Properties();
        String serverIp8 = "127.0.0.1:8848";
        setting8.put(PropertyKeyConst.SERVER_ADDR, serverIp8);
        setting8.put(PropertyKeyConst.USERNAME, "nacos");
        setting8.put(PropertyKeyConst.PASSWORD, "nacos");
        inaming8 = NacosFactory.createNamingService(setting7);
        
        Properties setting9 = new Properties();
        String serverIp9 = "127.0.0.1:8849";
        setting9.put(PropertyKeyConst.SERVER_ADDR, serverIp9);
        setting9.put(PropertyKeyConst.USERNAME, "nacos");
        setting9.put(PropertyKeyConst.PASSWORD, "nacos");
        inaming9 = NacosFactory.createNamingService(setting7);
        
        TimeUnit.SECONDS.sleep(20L);
    }
    
    @AfterClass
    public static void after() throws Exception {
        CountDownLatch latch = new CountDownLatch(applications.size());
        for (ConfigurableApplicationContext context : applications.values()) {
            new Thread(() -> {
                try {
                    System.out.println("start close : " + context);
                    context.close();
                    inaming7.shutDown();
                    inaming8.shutDown();
                    inaming9.shutDown();
                } catch (Exception ignore) {
                } finally {
                    System.out.println("finished close : " + context);
                    latch.countDown();
                }
            }).start();
        }
        latch.await();
    }
    
    private static void run(final int index, final CountDownLatch latch, final Class<?> cls) {
        Runnable runnable = () -> {
            try {
                EnvUtil.setIsStandalone(false);
                
                final String path = Paths.get(System.getProperty("user.home"), "/nacos-" + index + "/").toString();
                DiskUtils.deleteDirectory(path);
                
                System.setProperty("nacos.home", path);
                System.out.println("nacos.home is : [" + path + "]");
                
                Map<String, Object> properties = new HashMap<>();
                properties.put("server.port", "884" + (7 + index));
                properties.put("nacos.home", path);
                properties.put("nacos.logs.path",
                        Paths.get(System.getProperty("user.home"), "nacos-" + index, "/logs/").toString());
                properties.put("spring.jmx.enabled", false);
                properties.put("nacos.core.snowflake.worker-id", index + 1);
                MapPropertySource propertySource = new MapPropertySource("nacos_cluster_test", properties);
                ConfigurableEnvironment environment = new StandardServletEnvironment();
                environment.getPropertySources().addFirst(propertySource);
                SpringApplication cluster = new SpringApplicationBuilder(cls).web(WebApplicationType.SERVLET)
                        .environment(environment).properties(clusterInfo).properties("embeddedStorage=true").build();
                
                ConfigurableApplicationContext context = cluster.run();
                
                DistributedDatabaseOperateImpl operate = context.getBean(DistributedDatabaseOperateImpl.class);
                CPProtocol protocol = context.getBean(CPProtocol.class);
                
                protocol.protocolMetaData().subscribe(operate.group(), MetadataKey.LEADER_META_DATA, o -> {
                    ProtocolMetaData.ValueItem item = (ProtocolMetaData.ValueItem) o;
                    System.out.println("node : 884" + (7 + index) + "-> select leader is : " + item.getData());
                    if (FINISHED[index].compareAndSet(false, true)) {
                        latch.countDown();
                    }
                });
                
                new Thread(() -> {
                    try {
                        Thread.sleep(5000L);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (FINISHED[index].compareAndSet(false, true)) {
                            latch.countDown();
                        }
                    }
                });
                
                applications.put(String.valueOf(properties.get("server.port")), context);
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        };
        
        runnable.run();
    }
    
}
