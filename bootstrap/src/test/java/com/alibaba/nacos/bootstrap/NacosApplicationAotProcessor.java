/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.bootstrap;

import com.alibaba.nacos.NacosServerBasicApplication;
import com.alibaba.nacos.NacosServerWebApplication;
import com.alibaba.nacos.airegistry.NacosAiRegistry;
import com.alibaba.nacos.console.NacosConsole;
import com.alibaba.nacos.common.executor.ThreadPoolManager;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.core.listener.startup.NacosStartUpManager;
import com.alibaba.nacos.sys.env.DeploymentType;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.file.WatchFileCenter;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.nativex.FileNativeConfigurationWriter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.aot.AbstractAotProcessor;
import org.springframework.context.aot.ContextAotProcessor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Build-time multi-context AOT experiment. Run in a dedicated JVM with matching plugins/configuration.
 */
public final class NacosApplicationAotProcessor {
    
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        System.setProperty(AbstractAotProcessor.AOT_PROCESSING, "true");
        DeploymentType deployment =
            DeploymentType.getType(System.getProperty("nacos.deployment.type", "merged"));
        EnvUtil.setDeploymentType(deployment);
        List<GenericApplicationContext> contexts = new ArrayList<>();
        Properties initializers = new Properties();
        try {
            if (deployment == DeploymentType.CONSOLE) {
                GenericApplicationContext console =
                    capture("console", new SpringApplicationBuilder(NacosConsole.class));
                contexts.add(console);
                initializers.putAll(
                    NacosAotManifest.snapshot(console.getEnvironment(), console.getClassLoader()));
                initializers.setProperty("console",
                    new PhaseProcessor(NacosConsole.class, output, "console").generate(console));
            } else if (deployment == DeploymentType.SERVER || deployment == DeploymentType.MERGED) {
                GenericApplicationContext core =
                    capture("core", new SpringApplicationBuilder(NacosServerBasicApplication.class)
                        .web(WebApplicationType.NONE));
                contexts.add(core);
                initializers.putAll(
                    NacosAotManifest.snapshot(core.getEnvironment(), core.getClassLoader()));
                initializers.setProperty("core",
                    new PhaseProcessor(NacosServerBasicApplication.class, output, "core")
                        .generate(core));
                GenericApplicationContext web = capture("web",
                    new SpringApplicationBuilder(NacosServerWebApplication.class).parent(core));
                contexts.add(web);
                initializers.setProperty("web",
                    new PhaseProcessor(NacosServerWebApplication.class, output, "web")
                        .generate(web));
                if (deployment == DeploymentType.MERGED) {
                    GenericApplicationContext console = capture("console",
                        new SpringApplicationBuilder(NacosConsole.class).parent(core));
                    contexts.add(console);
                    initializers.setProperty("console",
                        new PhaseProcessor(NacosConsole.class, output, "console")
                            .generate(console));
                }
                if (NacosAotManifest.hasAiRegistry(core.getEnvironment())) {
                    GenericApplicationContext registry = capture("ai-registry",
                        new SpringApplicationBuilder(NacosAiRegistry.class).parent(core));
                    contexts.add(registry);
                    initializers.setProperty("ai-registry",
                        new PhaseProcessor(NacosAiRegistry.class, output, "aiRegistry")
                            .generate(registry));
                }
            } else {
                throw new IllegalArgumentException("Unsupported deployment: " + deployment);
            }
            if (ApplicationUtils.isStarted()) {
                throw new IllegalStateException("AOT processing must not mark the server ready");
            }
            Path index = output.resolve("resources/" + NacosAotInitializer.INDEX);
            Files.createDirectories(index.getParent());
            try (OutputStream stream = Files.newOutputStream(index)) {
                initializers.store(stream, "Nacos multi-context AOT manifest");
            }
            RuntimeHints hints = new RuntimeHints();
            hints.resources().registerPattern(NacosAotInitializer.INDEX);
            for (String service : NacosAotManifest.SERVICES) {
                hints.resources().registerPattern("META-INF/services/" + service);
                for (String provider : initializers.getProperty("spi." + service).split(",")) {
                    if (!provider.isEmpty()) {
                        hints.reflection().registerType(TypeReference.of(provider),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
                    }
                }
            }
            new FileNativeConfigurationWriter(output.resolve("resources"), "com.alibaba.nacos",
                "nacos-bootstrap-aot").write(hints);
            System.out.println("AOT_GENERATED deployment=" + deployment + ", datasource="
                + initializers.getProperty("datasource")
                + ", contexts=" + contexts.size());
        } finally {
            Collections.reverse(contexts);
            for (GenericApplicationContext context : contexts) {
                context.close();
            }
            WatchFileCenter.shutdown();
            NotifyCenter.shutdown();
            ThreadPoolManager.shutdown();
            System.clearProperty(AbstractAotProcessor.AOT_PROCESSING);
        }
    }
    
    private static GenericApplicationContext capture(String phase,
        SpringApplicationBuilder builder) {
        NacosStartUpManager.start(phase);
        try {
            SpringApplication.withHook(
                application -> new SpringApplicationRunListener() {
                    
                    @Override
                    public void contextLoaded(ConfigurableApplicationContext context) {
                        throw new SpringApplication.AbandonedRunException(context);
                    }
                }, () -> builder.run());
        } catch (SpringApplication.AbandonedRunException captured) {
            return (GenericApplicationContext) captured.getApplicationContext();
        }
        throw new IllegalStateException("Build-time context was unexpectedly refreshed");
    }
    
    private static final class PhaseProcessor extends ContextAotProcessor {
        
        private final String phase;
        
        private PhaseProcessor(Class<?> applicationClass, Path output, String phase) {
            super(applicationClass, Settings.builder()
                .sourceOutput(output.resolve(phase + "/sources"))
                .resourceOutput(output.resolve(phase + "/resources"))
                .classOutput(output.resolve(phase + "/classes"))
                .groupId("com.alibaba.nacos").artifactId("nacos-aot-poc-" + phase).build());
            this.phase = phase;
        }
        
        private String generate(GenericApplicationContext context) {
            // Keep the processed parent alive until the child has been processed as well.
            return performAotProcessing(context).canonicalName();
        }
        
        @Override
        protected ClassNameGenerator createClassNameGenerator() {
            return new ClassNameGenerator(ClassName.get(getApplicationClass()), phase);
        }
        
        @Override
        protected GenericApplicationContext prepareApplicationContext(Class<?> applicationClass) {
            throw new UnsupportedOperationException("Use the captured context");
        }
    }
}
