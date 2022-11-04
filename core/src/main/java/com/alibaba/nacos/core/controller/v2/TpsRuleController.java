package com.alibaba.nacos.core.controller.v2;

import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.plugin.control.ControlManagerFactory;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;
import com.alibaba.nacos.plugin.control.ruleactivator.ConnectionLimitRuleChangeEvent;
import com.alibaba.nacos.plugin.control.ruleactivator.LocalDiskRuleActivator;
import com.alibaba.nacos.plugin.control.ruleactivator.PersistRuleActivatorProxy;
import com.alibaba.nacos.plugin.control.ruleactivator.RuleParserProxy;
import com.alibaba.nacos.plugin.control.ruleactivator.TpsControlRuleChangeEvent;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(Commons.NACOS_CORE_CONTEXT_V2 + "/controlrule")
public class TpsRuleController {
    
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TpsRuleController.class);
    
    private ServerMemberManager serverMemberManager;
    
    private NacosAsyncRestTemplate nacosAsyncRestTemplate = HttpClientBeanHolder.getNacosAsyncRestTemplate(LOGGER);
    
    
    public TpsRuleController(ServerMemberManager serverMemberManager) {
        this.serverMemberManager = serverMemberManager;
        NotifyCenter.registerToPublisher(ConnectionLimitRuleChangeEvent.class, 16384);
        NotifyCenter.registerToPublisher(TpsControlRuleChangeEvent.class, 16384);
    }
    
    @GetMapping(value = "/tps/points")
    public RestResult<Set<String>> getTpsPointNames() {
        Set<String> pointNames = ControlManagerFactory.getInstance().getTpsControlManager().getPoints().keySet();
        return RestResultUtils.success(pointNames);
    }
    
    @GetMapping(value = "/tps/current")
    public RestResult<TpsControlRule> getTpsRule(@RequestParam(value = "pointname") String pointName) {
        TpsControlRule tpsControlRule = ControlManagerFactory.getInstance().getTpsControlManager().getRules()
                .get(pointName);
        return RestResultUtils.success(tpsControlRule);
    }
    
    @GetMapping(value = "/tps/currentall")
    public RestResult<Map<String, TpsControlRule>> getTpsRule() {
        Map<String, TpsControlRule> rules = ControlManagerFactory.getInstance().getTpsControlManager().getRules();
        return RestResultUtils.success(rules);
    }
    
    @GetMapping(value = "/tps/cluster")
    public RestResult<Map<String, TpsControlRule>> getClusterTpsRule(
            @RequestParam(value = "pointname") String pointName) {
        
        return RestResultUtils.success(getClusterTpsControl(pointName));
    }
    
    @PostMapping(value = "/tps")
    public RestResult<Boolean> updateTpsRule(@RequestParam(value = "pointname") String pointName,
            @RequestParam(value = "rulecontent") String content, @RequestParam(value = "persist") Boolean isPersist)
            throws Exception {
        try {
            RuleParserProxy.getInstance().parseTpsRule(content);
            if (isPersist) {
                PersistRuleActivatorProxy.getInstance().saveTpsRule(pointName, content);
            } else {
                LocalDiskRuleActivator.INSTANCE.saveTpsRule(pointName, content);
            }
            
            return RestResultUtils.success();
        } catch (Throwable throwable) {
            return RestResultUtils.failed("invalid content rule,error =" + throwable);
            
        }
        
    }
    
    @PostMapping(value = "/tps/reload/current")
    public RestResult<Void> reloadTpsRule(@RequestParam(value = "pointname") String pointName,
            @RequestParam(value = "persist") Boolean isPersist) throws Exception {
        
        NotifyCenter.publishEvent(new TpsControlRuleChangeEvent(pointName, isPersist));
        return RestResultUtils.success();
    }
    
    @PostMapping(value = "/tps/reload/cluster")
    public RestResult<Map<String, Boolean>> reloadClusterTpsRule(@RequestParam(value = "pointname") String pointName,
            @RequestParam(value = "persist") Boolean isPersist) throws Exception {
        return RestResultUtils.success(reloadClusterTpsControl(pointName, isPersist));
    }
    
    @GetMapping(value = "/connection/current")
    public RestResult<ConnectionLimitRule> getConnectionRule() {
        ConnectionLimitRule connectionLimitRule = ControlManagerFactory.getInstance().getConnectionControlManager()
                .getConnectionLimitRule();
        return RestResultUtils.success(connectionLimitRule);
    }
    
    @GetMapping(value = "/connection/cluster")
    public RestResult<Map<String, ConnectionLimitRule>> getClusterConnectionRule() {
        
        return RestResultUtils.success(getClusterConnectionControl());
    }
    
    @PostMapping(value = "/connection")
    public RestResult<Boolean> updateConnectionRule(@RequestParam(value = "rulecontent") String content,
            @RequestParam(value = "persist") Boolean isPersist) throws Exception {
        try {
            RuleParserProxy.getInstance().parseConnectionRule(content);
            if (isPersist) {
                
                PersistRuleActivatorProxy.getInstance().saveConnectionRule(content);
            } else {
                LocalDiskRuleActivator.INSTANCE.saveConnectionRule(content);
            }
            
            return RestResultUtils.success();
        } catch (Throwable throwable) {
            return RestResultUtils.failed("invalid rule content,cause=" + throwable);
            
        }
        
    }
    
    @PostMapping(value = "/connection/reload/current")
    public RestResult<Void> reloadConnectionRule(@RequestParam(value = "persist") Boolean isPersist) throws Exception {
        NotifyCenter.publishEvent(new ConnectionLimitRuleChangeEvent(isPersist));
        return RestResultUtils.success();
    }
    
    
    @PostMapping(value = "/connection/reload/cluster")
    public RestResult<Map<String, Boolean>> reloadClusterConnectionRule(
            @RequestParam(value = "persist") Boolean isPersist) throws Exception {
        Map<String, Boolean> clusterResult = reloadClusterConnectionControl(isPersist);
        return RestResultUtils.success(clusterResult);
    }
    
    
    private static final String TPSRULE_URL_PATTERN = "http://{0}{1}/v2/core/controlrule/tps/current";
    
    private Map<String, TpsControlRule> getClusterTpsControl(String pointName) {
        
        CountDownLatch latch = new CountDownLatch(serverMemberManager.allMembers().size());
        Map<String, TpsControlRule> clusterResult = new HashMap<>();
        
        for (Member member : serverMemberManager.allMembers()) {
            String url = MessageFormat.format(TPSRULE_URL_PATTERN, member.getAddress(), EnvUtil.getContextPath());
            Header header = Header.newInstance();
            Query query = Query.newInstance();
            query.addParam("pointname", pointName);
            nacosAsyncRestTemplate.get(url, header, query, RestResult.class, new Callback<TpsControlRule>() {
                
                @Override
                public void onReceive(RestResult<TpsControlRule> result) {
                    if (result.ok() && result.getData() != null) {
                        clusterResult.put(member.getIp(), result.getData());
                    } else {
                        LOGGER.warn("Fail to get tps control rule of member={},pointname={}", member.getAddress(),
                                pointName);
                    }
                    latch.countDown();
                }
                
                @Override
                public void onError(Throwable throwable) {
                    LOGGER.error("Error , get tps control rule of member={},pointname={},throwable={}",
                            member.getAddress(), pointName, throwable);
                    latch.countDown();
                }
                
                @Override
                public void onCancel() {
                    LOGGER.error("Canceled,  get tps control rule of member={},pointname={}", member.getAddress(),
                            pointName);
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await(3000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            LOGGER.error("Get cluster tps point Interrupted, get tps control rule ,pointname={}", pointName);
        }
        return clusterResult;
        
    }
    
    private static final String CONNECTION_RULE_URL_PATTERN = "http://{0}{1}/v2/core/controlrule/connection/current";
    
    private Map<String, ConnectionLimitRule> getClusterConnectionControl() {
        
        CountDownLatch latch = new CountDownLatch(serverMemberManager.allMembers().size());
        Map<String, ConnectionLimitRule> clusterResult = new HashMap<>();
        
        for (Member member : serverMemberManager.allMembers()) {
            String url = MessageFormat
                    .format(CONNECTION_RULE_URL_PATTERN, member.getAddress(), EnvUtil.getContextPath());
            Header header = Header.newInstance();
            Query query = Query.newInstance();
            nacosAsyncRestTemplate.get(url, header, query, RestResult.class, new Callback<ConnectionLimitRule>() {
                
                @Override
                public void onReceive(RestResult<ConnectionLimitRule> result) {
                    if (result.ok() && result.getData() != null) {
                        clusterResult.put(member.getIp(), result.getData());
                    } else {
                        LOGGER.warn("Fail to get connection control rule of member={}", member.getAddress());
                    }
                    latch.countDown();
                }
                
                @Override
                public void onError(Throwable throwable) {
                    LOGGER.error("Error , get connection control rule of member={},throwable={}", member.getAddress(),
                            throwable);
                    latch.countDown();
                }
                
                @Override
                public void onCancel() {
                    LOGGER.error("Canceled,  get connection control rule of member={}", member.getAddress());
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await(3000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            LOGGER.error("Get cluster connection  Interrupted");
        }
        return clusterResult;
        
    }
    
    
    private static final String RELOAD_CONNECTION_RULE_URL_PATTERN = "http://{0}{1}/v2/core/controlrule/connection/reload/current";
    
    private Map<String, Boolean> reloadClusterConnectionControl(boolean isPersist) {
        
        CountDownLatch latch = new CountDownLatch(serverMemberManager.allMembers().size());
        Map<String, Boolean> clusterResult = new HashMap<>();
        
        for (Member member : serverMemberManager.allMembers()) {
            String url = MessageFormat
                    .format(RELOAD_CONNECTION_RULE_URL_PATTERN, member.getAddress(), EnvUtil.getContextPath());
            Header header = Header.newInstance();
            Map<String, String> bodyValues = new HashMap<>();
            bodyValues.put("persist", String.valueOf(isPersist));
            nacosAsyncRestTemplate.postForm(url, header, bodyValues, RestResult.class, new Callback<Boolean>() {
                
                @Override
                public void onReceive(RestResult<Boolean> result) {
                    if (result.ok()) {
                        clusterResult.put(member.getIp(), Boolean.TRUE);
                    } else {
                        LOGGER.warn("Fail to get connection control rule of member={}", member.getAddress());
                        clusterResult.put(member.getIp(), Boolean.FALSE);
                        
                    }
                    latch.countDown();
                }
                
                @Override
                public void onError(Throwable throwable) {
                    LOGGER.error("Error , get connection control rule of member={},throwable={}", member.getAddress(),
                            throwable);
                    latch.countDown();
                }
                
                @Override
                public void onCancel() {
                    LOGGER.error("Canceled,  get connection control rule of member={}", member.getAddress());
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await(3000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            LOGGER.error("Get cluster connection  Interrupted");
        }
        return clusterResult;
        
    }
    
    
    private static final String RELOAD_TPS_RULE_URL_PATTERN = "http://{0}{1}/v2/core/controlrule/tps/reload/current";
    
    private Map<String, Boolean> reloadClusterTpsControl(String pointName, boolean isPersist) {
        
        CountDownLatch latch = new CountDownLatch(serverMemberManager.allMembers().size());
        Map<String, Boolean> clusterResult = new HashMap<>();
        
        for (Member member : serverMemberManager.allMembers()) {
            String url = MessageFormat
                    .format(RELOAD_TPS_RULE_URL_PATTERN, member.getAddress(), EnvUtil.getContextPath());
            Header header = Header.newInstance();
            Map<String, String> bodyValues = new HashMap<>();
            bodyValues.put("pointname", pointName);
            bodyValues.put("persist", String.valueOf(isPersist));
            nacosAsyncRestTemplate.postForm(url, header, bodyValues, RestResult.class, new Callback<Void>() {
                
                @Override
                public void onReceive(RestResult<Void> result) {
                    if (result.ok()) {
                        clusterResult.put(member.getIp(), Boolean.TRUE);
                    } else {
                        clusterResult.put(member.getIp(), Boolean.FALSE);
                        LOGGER.warn("Fail to get connection control rule of member={}", member.getAddress());
                    }
                    latch.countDown();
                }
                
                @Override
                public void onError(Throwable throwable) {
                    LOGGER.error("Error , get connection control rule of member={},throwable={}", member.getAddress(),
                            throwable);
                    clusterResult.put(member.getIp(), Boolean.FALSE);
                    latch.countDown();
                }
                
                @Override
                public void onCancel() {
                    LOGGER.error("Canceled,  get connection control rule of member={}", member.getAddress());
                    clusterResult.put(member.getIp(), Boolean.FALSE);
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await(3000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            LOGGER.error("Get cluster connection  Interrupted");
        }
        return clusterResult;
        
    }
}
