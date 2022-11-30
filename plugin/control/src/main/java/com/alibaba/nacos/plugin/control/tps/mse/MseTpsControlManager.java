package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsBarrierCreatorProxy;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * abstract tps control manager.
 */
public class MseTpsControlManager extends TpsControlManager {
    
    public MseTpsControlManager() {
    }
    
    /**
     * apple tps rule.
     *
     * @param pointName pointName.
     */
    public synchronized void registerTpsPoint(String pointName) {
        if (!super.getPoints().containsKey(pointName)) {
            super.getPoints().put(pointName, TpsBarrierCreatorProxy.getTpsBarrierCreator().createTpsBarrier(pointName));
            if (super.getRules().containsKey(pointName)) {
                super.getPoints().get(pointName).applyRule(super.getRules().get(pointName));
            } else {
                super.initTpsRule(pointName);
            }
        }
    }
    
    @Override
    protected void startTpsReport() {
        super.executorService
                .scheduleWithFixedDelay(new MseTpsControlManager.TpsMetricsReporter(), 0, 900, TimeUnit.MILLISECONDS);
        
    }
    
    /**
     * check tps result.
     *
     * @param tpsRequest TpsRequest.
     * @return check current tps is allowed.
     */
    public TpsCheckResponse check(TpsCheckRequest tpsRequest) {
        
        if (!(tpsRequest instanceof MseTpsCheckRequest)) {
            return super.check(tpsRequest);
        }
        MseTpsCheckRequest mseTpsCheckRequest = (MseTpsCheckRequest) tpsRequest;
        if (super.getPoints().containsKey(tpsRequest.getPointName())) {
            try {
                return super.getPoints().get(mseTpsCheckRequest.getPointName()).applyTps(mseTpsCheckRequest);
            } catch (Throwable throwable) {
                Loggers.TPS.warn("[{}]apply tps error,clientIp={},connectionId={},keys={},error={}",
                        mseTpsCheckRequest.getPointName(), mseTpsCheckRequest.getClientIp(),
                        mseTpsCheckRequest.getConnectionId(), mseTpsCheckRequest.getMonitorKeys(), throwable);
            }
        }
        return new TpsCheckResponse(true, TpsResultCode.CHECK_SKIP, "skip");
        
    }
    
    
    class TpsMetricsReporter implements Runnable {
        
        long lastReportSecond = 0L;
        
        long lastReportMinutes = 0L;
        
        long lastReportHours = 0L;
        
        /**
         * get format string "2021-01-16 17:20:21" of timestamp.
         *
         * @param timeStamp timestamp milliseconds.
         * @return
         */
        public String getTimeFormatOfSecond(long timeStamp) {
            String format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timeStamp));
            return format;
        }
        
        @Override
        public void run() {
            try {
                long now = System.currentTimeMillis();
                StringBuilder stringBuilder = new StringBuilder();
                Set<Map.Entry<String, TpsBarrier>> entries = MseTpsControlManager.super.getPoints().entrySet();
                
                long tempSecond = 0L;
                long tempMinutes = 0L;
                long tempHours = 0L;
                
                long metricsTime = now - 1000L;
                String formatString = getTimeFormatOfSecond(metricsTime);
                for (Map.Entry<String, TpsBarrier> entry : entries) {
                    MseTpsBarrier tpsBarrier = (MseTpsBarrier) entry.getValue();
                    String pointName = entry.getKey();
                    TpsMetrics metrics = tpsBarrier.getPointBarrier().getMetrics(metricsTime);
                    if (metrics != null) {
                        //already reported.
                        if (lastReportSecond != 0L && lastReportSecond == metrics.getTimeStamp()) {
                            continue;
                        }
                        tempSecond = metrics.getTimeStamp();
                        
                        stringBuilder.append(pointName).append("|").append("point").append("|")
                                .append(metrics.getPeriod()).append("|").append(formatString).append("|")
                                .append(metrics.getCounter().getPassCount()).append("|")
                                .append(metrics.getCounter().getDeniedCount()).append("|").append("\n");
                    }
                    List<MseRuleBarrier> patternBarriers = tpsBarrier.getPatternBarriers();
                    
                    for (MseRuleBarrier tpsPatternBarrier : patternBarriers) {
                        MseTpsMetrics patternMetrics = (MseTpsMetrics) tpsPatternBarrier
                                .getMetrics(now - tpsPatternBarrier.getPeriod().toMillis(1));
                        if (patternMetrics == null) {
                            continue;
                        }
                        
                        //already reported.
                        if (patternMetrics.getPeriod() == TimeUnit.SECONDS) {
                            if (lastReportSecond != 0L && lastReportSecond == patternMetrics.getTimeStamp()) {
                                continue;
                            }
                        }
                        if (patternMetrics.getPeriod() == TimeUnit.MINUTES) {
                            if (lastReportMinutes != 0L && lastReportMinutes == patternMetrics.getTimeStamp()) {
                                continue;
                            }
                            tempMinutes = patternMetrics.getTimeStamp();
                        }
                        if (patternMetrics.getPeriod() == TimeUnit.HOURS) {
                            if (lastReportHours != 0L && lastReportHours == patternMetrics.getTimeStamp()) {
                                continue;
                            }
                            tempHours = patternMetrics.getTimeStamp();
                        }
                        
                        //check if print detail log.
                        boolean printDetail = false;
                        
                        String patternMonitorName = tpsPatternBarrier.getRuleName();
                        Map<String, TpsControlRule> rules = MseTpsControlManager.super.getRules();
                        if (rules != null && rules.get(pointName) != null
                                && ((MseTpsControlRule) rules.get(pointName)).getMonitorKeyRule() != null &&
                                ((MseTpsControlRule) rules.get(pointName)).getMonitorKeyRule().get(patternMonitorName)
                                        != null) {
                            printDetail = ((MseTpsControlRule) rules.get(pointName)).getMonitorKeyRule()
                                    .get(patternMonitorName).isPrintLog();
                        }
                        
                        if (!printDetail) {
                            continue;
                        }
                        TpsMetrics.Counter fuzzyCounter = patternMetrics.getCounter();
                        if (fuzzyCounter != null) {
                            stringBuilder.append(pointName).append("|").append(patternMonitorName).append("|")
                                    .append("fuzzy").append("|").append(patternMetrics.getPeriod()).append("|")
                                    .append(formatString).append("|").append(fuzzyCounter.getPassCount()).append("|")
                                    .append(fuzzyCounter.getDeniedCount()).append("|").append("\n");
                        }
                        
                        Map<String, TpsMetrics.Counter> protoKeyCounters = patternMetrics.getProtoKeyCounter();
                        if (protoKeyCounters != null && !protoKeyCounters.isEmpty()) {
                            for (Map.Entry<String, TpsMetrics.Counter> protoKeyCounter : protoKeyCounters.entrySet()) {
                                
                                stringBuilder.append(pointName).append("|").append(patternMonitorName).append("|")
                                        .append("proto").append("|").append(patternMetrics.getPeriod()).append("|")
                                        .append(formatString).append("|").append(protoKeyCounter.getKey()).append("|")
                                        .append(protoKeyCounter.getValue().getSimpleLog()).append("|").append("\n");
                            }
                            
                        }
                    }
                }
                
                if (tempSecond > 0) {
                    lastReportSecond = tempSecond;
                }
                if (tempMinutes > 0) {
                    lastReportMinutes = tempMinutes;
                }
                if (tempHours > 0) {
                    lastReportHours = tempHours;
                }
                
                if (stringBuilder.length() > 0) {
                    Loggers.TPS.info("Tps reporting...\n" + stringBuilder.toString());
                }
            } catch (Throwable throwable) {
                Loggers.TPS.error("Tps reporting error", throwable);
            }
            
        }
    }
}
