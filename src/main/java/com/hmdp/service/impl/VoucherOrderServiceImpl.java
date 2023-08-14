package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.AddOrderDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.MqConstant;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setResultType(Long.class);
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    }

    //阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //这个类刚初始化后就去执行这个任务
    //@PostConstruct
    //private void init() {
    //    SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHndler());
    //}

    //public void handleVoucherOrder(VoucherOrder voucherOrder) {
    //    Long userId = voucherOrder.getUserId();
    //    RLock lock = redissonClient.getLock("lock:order:" + userId);
    //    if (!lock.tryLock()) {
    //        //获取锁失败
    //        log.error("不允许重复下单");
    //    }
    //    try {
    //        //获取代理对象（事务）
    //        proxy.createVoucherOrder(voucherOrder);
    //    } finally {
    //        //释放锁
    //        lock.unlock();
    //    }
    //}

    //private IVoucherOrderService proxy;


    //private class VoucherOrderHndler implements Runnable {
    //
    //    @Override
    //    public void run() {
    //        while (true) {
    //            try {
    //                //1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order >
    //                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
    //                        Consumer.from("g1", "c1"),
    //                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
    //                        StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
    //                );
    //                //2.判断消息获取是否成功
    //                if (list == null || list.isEmpty()) {
    //                    //如果获取失败，说明没有消息，继续下一次循环
    //                    continue;
    //                }
    //                //3.解析消息中的订单信息
    //                MapRecord<String, Object, Object> record = list.get(0);
    //                Map<Object, Object> values = record.getValue();
    //                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
    //                //4.如果获取成功，可以下单
    //                handleVoucherOrder(voucherOrder);
    //                //5.ACK确认 SACK stream.orders g1 id
    //                stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", record.getId());
    //            } catch (Exception e) {
    //                log.error("处理异常:{}", e);
    //                handlePendingList();
    //            }
    //        }
    //    }
    //
    //    private void handlePendingList() {
    //        while (true) {
    //            try {
    //                //1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS streams.order 0
    //                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
    //                        Consumer.from("g1", "c1"),
    //                        StreamReadOptions.empty().count(1),
    //                        StreamOffset.create("stream.orders", ReadOffset.from("0"))
    //                );
    //                //2.判断消息获取是否成功
    //                if (list == null || list.isEmpty()) {
    //                    //如果获取失败，说明pending-list没有异常消息，结束循环
    //                    break;
    //                }
    //                //3.解析消息中的订单信息
    //                MapRecord<String, Object, Object> record = list.get(0);
    //                Map<Object, Object> values = record.getValue();
    //                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
    //                //4.如果获取成功，可以下单
    //                handleVoucherOrder(voucherOrder);
    //                //5.ACK确认 SACK stream.orders g1 id
    //                stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", record.getId());
    //            } catch (Exception e) {
    //                log.error("处理pending-list订单异常:{}", e);
    //
    //            }
    //        }
    //    }
    //}

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //生成订单id
        long orderId = redisIdWorker.nextId("order");
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId));
        //2.判断结果是否为0
        int r = result.intValue();
        if (r != 0) {
            //2.不为0，没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重读下单");
        }

        //3.rocketmq发送下单消息
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        Message<String> message = MessageBuilder.withPayload(JSON.toJSONString(voucherOrder)).build();
        rocketMQTemplate.asyncSend(MqConstant.ORDER_TOPIC + ":" + MqConstant.ADD_ORDER, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("发送成功");
            }

            @Override
            public void onException(Throwable throwable) {
                rocketMQTemplate.syncSend(MqConstant.ORDER_TOPIC + ":" + MqConstant.ADD_ORDER, message);
            }
        });

        //3.获取代理对象
        //proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {

        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        if (!lock.tryLock()) {
            //获取锁失败
            log.error("不允许重复下单");
        }
        try {
            //4.一人一单
            //4.1.查询是否已经有订单
            int count = lambdaQuery().eq(VoucherOrder::getUserId, userId).eq(VoucherOrder::getVoucherId, voucherOrder.getVoucherId()).count();
            if (count > 0) {
                //已有订单
                log.error("用户已经购买过一次！");
                return;
            }

            //5.扣减库存
            //5.1.写法一
            LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.setSql("stock=stock-1").eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId()).gt(SeckillVoucher::getStock, 0);
            boolean success = seckillVoucherService.update(updateWrapper);

            if (!success) {
                //扣减失败
                log.error("库存不足");
                return;
            }

            //创建订单
            save(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
    }
}
