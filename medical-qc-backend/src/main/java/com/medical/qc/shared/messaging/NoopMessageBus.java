package com.medical.qc.shared.messaging;

import org.springframework.stereotype.Component;

/**
 * 空实现消息总线。
 *
 * <p>当 JMS 未启用或未配置时，返回 false 以便上层执行同步或本地兜底逻辑。</p>
 */
@Component
public class NoopMessageBus implements MessageBus {

    @Override
    public boolean send(String destination, Object payload) {
        // 明确返回 false，通知调用方走本地线程池或同步兜底逻辑。
        return false;
    }
}

