package com.hmdp.listener;

import com.alibaba.fastjson.JSON;
import com.hmdp.dto.AddOrderDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.MqConstant;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.SelectorType;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author 朱焕杰
 * @version 1.0
 * @description TODO
 * @date 2023/6/15 21:00
 */
@Component
@RocketMQMessageListener(topic = MqConstant.ORDER_TOPIC,
        consumerGroup = MqConstant.ADD_ORDER_GROUP,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        selectorType = SelectorType.TAG,//tah过滤模式
        selectorExpression = MqConstant.ADD_ORDER

)
public class AddOrderListener implements RocketMQListener<MessageExt> {

    @Autowired
    private IVoucherOrderService voucherOrderService;


    @Override
    public void onMessage(MessageExt messageExt) {
        VoucherOrder voucherOrder = JSON.parseObject(new String(messageExt.getBody()), VoucherOrder.class);
        voucherOrderService.createVoucherOrder(voucherOrder);
    }
}
