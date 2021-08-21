package com.alibaba.nacos.naming.manager.test;

import com.alibaba.nacos.naming.manager.MetricsMonitorManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MetricsManagerTest {
    
    // counter 测试
    @Scheduled(cron = "0/5 * * * * ?")
    public void testCounterOne() {
        Counter counter = MetricsMonitorManager.counter("nacos_monitor", "module", "naming", "name", "counterOne");
        counter.increment();
    }
    
    @Scheduled(cron = "0/5 * * * * ?")
    public void testCounterTwo() {
        Counter counter = Metrics.counter("nacos_monitor", "module", "naming", "name", "counterTwo");
        counter.increment();
    }
    
    // timer 测试
    @Scheduled(cron = "0/5 * * * * ?")
    public void testTimerOne() {
        Timer timer = MetricsMonitorManager.timer("nacos_monitor", "module", "naming", "name", "timerOne");
        timer.record(10, TimeUnit.SECONDS);
    }
    
    
    @Scheduled(cron = "0/5 * * * * ?")
    public void testTimerTwo() {
        Metrics.timer("nacos_monitor", "module", "naming", "name", "timerTwo").record(10, TimeUnit.SECONDS);
    }
    
    // gauge 测试
    @Scheduled(cron = "0/5 * * * * ?")
    public void testGaugeOne() {
        AtomicLong gauge = MetricsMonitorManager.gauge("nacos_monitor", "module", "naming", "name", "gaugeOne");
        gauge.incrementAndGet();
    }
    
    private AtomicLong testGauge = new AtomicLong();
    @Scheduled(cron = "0/5 * * * * ?")
    public void testGaugeTwo() {
        Metrics.gauge("nacos_monitor", Arrays.asList(new ImmutableTag("module", "naming"), new ImmutableTag("name", "gaugeTwo")), testGauge);
        testGauge.incrementAndGet();
        
    }
}
