package com.medical.qc.shared.ai;

import java.util.Map;

/**
 * AI 推理网关。
 *
 * <p>当前先承接脑出血检测模型调用，后续可扩展到更多影像质控算法。</p>
 */
public interface AiGateway {

    /**
     * 调用脑出血检测模型。
     *
     * @param imagePath 服务器本地影像绝对路径
     * @return 推理结果；若调用失败，返回包含 error 字段的结果映射
     */
    Map<String, Object> analyzeHemorrhage(String imagePath);
}

