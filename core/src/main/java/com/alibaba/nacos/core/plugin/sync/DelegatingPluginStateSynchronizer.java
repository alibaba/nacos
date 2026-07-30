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

package com.alibaba.nacos.core.plugin.sync;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.plugin.condition.ConditionOnClusterMode;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.alibaba.nacos.core.utils.ClassUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Cluster-only delegate that selects and owns one plugin state synchronizer.
 *
 * @author Nacos
 */
@Component
@Conditional(ConditionOnClusterMode.class)
public class DelegatingPluginStateSynchronizer implements PluginStateSynchronizer {
    
    static final String TYPE_PROPERTY = "nacos.plugin.state.synchronizer.type";
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(DelegatingPluginStateSynchronizer.class);
    
    private static final ExecutorService INITIALIZATION_EXECUTOR =
        ExecutorFactory.Managed.newSingleExecutorService(
            ClassUtils.getCanonicalName(DelegatingPluginStateSynchronizer.class),
            new NameThreadFactory("nacos-plugin-synchronizer"));
    
    private final Supplier<String> typeSupplier;
    
    private final Supplier<Collection<PluginStateSynchronizerProvider>> providerSupplier;
    
    private final PluginStateSynchronizerProvider builtInProvider;
    
    private final PluginStateSynchronizationContext context;
    
    private final Executor executor;
    
    private final AtomicReference<InitializationState> state =
        new AtomicReference<>(InitializationState.NEW);
    
    private volatile PluginStateSynchronizer delegate;
    
    private volatile String selectedName = "unresolved";
    
    private volatile Throwable failure;
    
    @Autowired
    public DelegatingPluginStateSynchronizer(PluginStatePersistenceService persistence,
        ObjectProvider<PluginStateApplier> applierProvider,
        ObjectProvider<PluginStateConsensusService> consensusServiceProvider) {
        this(() -> EnvUtil.getProperty(TYPE_PROPERTY),
            () -> NacosServiceLoader.load(PluginStateSynchronizerProvider.class),
            new RaftPluginStateSynchronizerProvider(consensusServiceProvider::getIfAvailable),
            new DefaultPluginStateSynchronizationContext(persistence,
                applierProvider::getIfAvailable),
            INITIALIZATION_EXECUTOR);
    }
    
    DelegatingPluginStateSynchronizer(Supplier<String> typeSupplier,
        Supplier<Collection<PluginStateSynchronizerProvider>> providerSupplier,
        PluginStateSynchronizerProvider builtInProvider,
        PluginStateSynchronizationContext context, Executor executor) {
        this.typeSupplier = typeSupplier;
        this.providerSupplier = providerSupplier;
        this.builtInProvider = builtInProvider;
        this.context = context;
        this.executor = executor;
    }
    
    @Override
    public void initialize() {
        if (!state.compareAndSet(InitializationState.NEW,
            InitializationState.INITIALIZING)) {
            return;
        }
        try {
            executor.execute(this::initializeSelectedSynchronizer);
        } catch (RuntimeException e) {
            markUnavailable(e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        PluginStateSynchronizer current = delegate;
        if (InitializationState.INITIALIZED != state.get() || current == null) {
            return false;
        }
        try {
            return current.isAvailable();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
    
    @Override
    public void syncStateChange(String pluginId, boolean enabled) throws NacosApiException {
        PluginStateSynchronizer current = getAvailableSynchronizer();
        try {
            current.syncStateChange(pluginId, enabled);
        } catch (NacosApiException e) {
            throw e;
        } catch (RuntimeException | LinkageError e) {
            throw synchronizationFailure(e);
        }
    }
    
    @Override
    public void syncConfigChange(String pluginId, Map<String, String> config)
        throws NacosApiException {
        PluginStateSynchronizer current = getAvailableSynchronizer();
        try {
            current.syncConfigChange(pluginId, config);
        } catch (NacosApiException e) {
            throw e;
        } catch (RuntimeException | LinkageError e) {
            throw synchronizationFailure(e);
        }
    }
    
    @Override
    @PreDestroy
    public void shutdown() {
        state.set(InitializationState.UNAVAILABLE);
        shutdownDelegate();
    }
    
    InitializationState getState() {
        return state.get();
    }
    
    String getSelectedName() {
        return selectedName;
    }
    
    private void initializeSelectedSynchronizer() {
        try {
            Selection selection = select(typeSupplier, providerSupplier, builtInProvider);
            selectedName = selection.getName();
            failure = selection.getFailure();
            PluginStateSynchronizerProvider selectedProvider = selection.getProvider();
            if (selectedProvider == null) {
                markUnavailable(failure);
                return;
            }
            PluginStateSynchronizer created =
                selectedProvider.createSynchronizer(context);
            if (created == null) {
                throw new IllegalStateException("Synchronizer provider returned null");
            }
            delegate = created;
            created.initialize();
            if (!state.compareAndSet(InitializationState.INITIALIZING,
                InitializationState.INITIALIZED)) {
                shutdownDelegate();
                return;
            }
            LOGGER.info("[DelegatingPluginStateSynchronizer] Started synchronizer: {}",
                selectedName);
        } catch (RuntimeException | LinkageError e) {
            markUnavailable(e);
        }
    }
    
    private PluginStateSynchronizer getAvailableSynchronizer() throws NacosApiException {
        if (!isAvailable()) {
            throw synchronizationFailure(failure);
        }
        return delegate;
    }
    
    private NacosApiException synchronizationFailure(Throwable cause) {
        String message = "Plugin state synchronizer '" + selectedName
            + "' is unavailable";
        if (cause == null) {
            return new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                message);
        }
        return new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, cause,
            message);
    }
    
    private void markUnavailable(Throwable cause) {
        failure = cause;
        state.set(InitializationState.UNAVAILABLE);
        shutdownDelegate();
        LOGGER.error("[DelegatingPluginStateSynchronizer] Failed to initialize synchronizer '{}'. "
            + "Nacos startup continues with cluster plugin writes unavailable.", selectedName,
            cause);
    }
    
    private void shutdownDelegate() {
        PluginStateSynchronizer current = delegate;
        delegate = null;
        if (current == null) {
            return;
        }
        try {
            current.shutdown();
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("[DelegatingPluginStateSynchronizer] Failed to shut down synchronizer "
                + "'{}'.", selectedName, e);
        }
    }
    
    private Selection select(Supplier<String> typeSupplier,
        Supplier<Collection<PluginStateSynchronizerProvider>> providerSupplier,
        PluginStateSynchronizerProvider builtInProvider) {
        try {
            String configuredType = typeSupplier.get();
            String selectedType = StringUtils.isBlank(configuredType)
                ? RaftPluginStateSynchronizerProvider.NAME : configuredType.trim();
            if (RaftPluginStateSynchronizerProvider.NAME.equals(selectedType)) {
                if (builtInProvider == null) {
                    return Selection.failure(selectedType,
                        new IllegalStateException("Built-in Raft synchronizer is unavailable"));
                }
                LOGGER.info("[DelegatingPluginStateSynchronizer] Selected built-in synchronizer: "
                    + "{}", selectedType);
                return Selection.success(selectedType, builtInProvider);
            }
            return selectExternalProvider(selectedType, providerSupplier);
        } catch (RuntimeException | LinkageError | ServiceConfigurationError e) {
            return Selection.failure("unknown", e);
        }
    }
    
    private Selection selectExternalProvider(String selectedType,
        Supplier<Collection<PluginStateSynchronizerProvider>> providerSupplier) {
        Collection<PluginStateSynchronizerProvider> providers = providerSupplier.get();
        List<ProviderCandidate> matching = new ArrayList<>();
        if (providers != null) {
            for (PluginStateSynchronizerProvider provider : providers) {
                if (provider == null) {
                    throw new IllegalStateException("Discovered null synchronizer provider");
                }
                String name = provider.getName();
                if (StringUtils.isBlank(name)) {
                    throw new IllegalStateException("Synchronizer provider has no name: "
                        + provider.getClass().getName());
                }
                if (selectedType.equals(name)) {
                    matching.add(new ProviderCandidate(provider,
                        provider.getClass().getName()));
                }
            }
        }
        matching.sort(Comparator.comparing(ProviderCandidate::getClassName));
        if (matching.isEmpty()) {
            return Selection.failure(selectedType,
                new IllegalStateException("No synchronizer provider found: " + selectedType));
        }
        ProviderCandidate selected = matching.get(0);
        for (int i = 1; i < matching.size(); i++) {
            LOGGER.warn("[DelegatingPluginStateSynchronizer] Ignore duplicate synchronizer "
                + "provider, name={}, selectedClass={}, ignoredClass={}.", selectedType,
                selected.getClassName(), matching.get(i).getClassName());
        }
        LOGGER.info("[DelegatingPluginStateSynchronizer] Selected external synchronizer: {}",
            selectedType);
        return Selection.success(selectedType, selected.getProvider());
    }
    
    enum InitializationState {
        
        NEW,
        
        INITIALIZING,
        
        INITIALIZED,
        
        UNAVAILABLE
    }
    
    private static class ProviderCandidate {
        
        private final PluginStateSynchronizerProvider provider;
        
        private final String className;
        
        ProviderCandidate(PluginStateSynchronizerProvider provider, String className) {
            this.provider = provider;
            this.className = className;
        }
        
        PluginStateSynchronizerProvider getProvider() {
            return provider;
        }
        
        String getClassName() {
            return className;
        }
    }
    
    private static class Selection {
        
        private final String name;
        
        private final PluginStateSynchronizerProvider provider;
        
        private final Throwable failure;
        
        Selection(String name, PluginStateSynchronizerProvider provider, Throwable failure) {
            this.name = name;
            this.provider = provider;
            this.failure = failure;
        }
        
        static Selection success(String name, PluginStateSynchronizerProvider provider) {
            return new Selection(name, provider, null);
        }
        
        static Selection failure(String name, Throwable failure) {
            return new Selection(name, null, failure);
        }
        
        String getName() {
            return name;
        }
        
        PluginStateSynchronizerProvider getProvider() {
            return provider;
        }
        
        Throwable getFailure() {
            return failure;
        }
    }
}
