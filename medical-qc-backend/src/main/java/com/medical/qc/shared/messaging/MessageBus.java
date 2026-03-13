package com.medical.qc.shared.messaging;

/**
 * 统一消息总线抽象。
 */
public interface MessageBus {

    /**
     * 发送消息。
     * 数据链路：业务服务 -> MessageBus -> JMS / Noop 实现 -> 成功返回 true，失败返回 false 供上层降级。
     *
     * @param destination 目标地址
     * @param payload 消息体
     * @return true 表示成功投递到消息基础设施，false 表示未投递成功
     */
    boolean send(String destination, Object payload);
}

