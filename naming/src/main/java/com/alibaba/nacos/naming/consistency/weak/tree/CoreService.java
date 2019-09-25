package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.pojo.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @author satjd
 */
@DependsOn({"datumStoreService","subscriberManager"})
@Component
public class CoreService {
    @Autowired
    TransferService transferService;

    @Autowired
    DatumStoreService datumStoreService;

    @Autowired
    SubscriberManager subscriberManager;

    private boolean initialized;

    @PostConstruct
    public void init() {
        initialized = true;
    }

    public void signalPublish(String key, Record value) throws Exception {
        // todo: 1 写自己的ack位图

        Datum d = new Datum();
        d.key = key;
        d.value = value;
        d = datumStoreService.getTimestampMarkedDatum(d);

        // todo: 2 存储消息到内存已完成，未落盘
        boolean putSuccessfully = datumStoreService.putDatum(d);
        if (!putSuccessfully) {
            // todo 这里是否不继续往下传需要考虑
            return;
        }

        // 3 传输消息并通知listener
        subscriberManager.addTask(key, ApplyAction.CHANGE);
        // subscriberManager#processNow(key,ApplyAction.CHANGE);

        transferService.transferNext(d,DatumType.UPDATE);
        Loggers.TREE.info("data added/updated, key={}", d.key);

        // todo 4 检查ack位图，写成功的节点到一定阈值就返回成功

    }

    public void onPublish(Datum datum, TreePeer source) throws Exception {
        // todo: 1 写自己的ack位图

        if (!datumStoreService.datumVersionValidate(datum)) {
            // 收到重复或过时消息
            return;
        }

        // todo: 2 存储消息到内存已完成，未落盘
        boolean putSuccessfully = datumStoreService.putDatum(datum);
        if (!putSuccessfully) {
            return;
        }

        // 3 继续向下传输消息并通知listener
        subscriberManager.addTask(datum.key,ApplyAction.CHANGE);
        // subscriberManager#processNow(datum.key,ApplyAction.CHANGE);

        transferService.transferNext(datum,DatumType.UPDATE,source);

        Loggers.TREE.info("data added/updated, key={}", datum.key);
    }

    public void signalDelete(String key) throws Exception {
        // todo: 1 写自己的ack位图
        Datum d = new Datum();
        d.key = key;
        d = datumStoreService.getTimestampMarkedDatum(d);

        // todo: 2 从cache中把key下的消息删除已完成，没删除磁盘上的文件
        boolean deleteSuccessfully = datumStoreService.removeDatum(key);
        if (!deleteSuccessfully) {
            Loggers.TREE.error("data delete failed. key={}",key);
            return;
        }

        // 3 传输消息并通知listener删除消息
         subscriberManager.addTask(key,ApplyAction.DELETE);
        // subscriberManager#processNow(key,ApplyAction.DELETE);

        transferService.transferNext(d,DatumType.DELETE);

        // todo 4 检查ack位图，写成功的节点到一定阈值就返回成功

        Loggers.TREE.info("data deleted, key={}", key);
    }

    public void onDelete(Datum datum, TreePeer source) throws Exception {
        // todo: 1 写自己的ack位图

        if (!datumStoreService.datumVersionValidate(datum)) {
            // 收到重复或过时消息
            return;
        }

        // todo: 2 从cache中把key下的消息删除已完成，没删除磁盘上的文件
        boolean deleteSuccessfully = datumStoreService.removeDatum(datum.key);
        if (!deleteSuccessfully) {
            Loggers.TREE.error("data delete failed. key={}",datum.key);
            return;
        }

        // 3 传输消息并通知listener删除消息
         subscriberManager.addTask(datum.key,ApplyAction.DELETE);
        // subscriberManager#processNow(datum.key,ApplyAction.DELETE);

        transferService.transferNext(datum,DatumType.DELETE,source);

        Loggers.TREE.info("data deleted, key={}", datum.key);
    }

    public boolean isInitialized() {
        return initialized;
    }
}
