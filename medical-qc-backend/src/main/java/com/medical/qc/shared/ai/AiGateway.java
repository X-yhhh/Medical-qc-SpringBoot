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

    /**
     * 调用头部 CT 平扫质控模型。
     *
     * @param volumePath 服务器本地 NIfTI 体数据绝对路径
     * @return 推理结果；若调用失败，返回包含 error 字段的结果映射
     */
    Map<String, Object> analyzeHeadQuality(String volumePath);

    /**
     * 调用 CT 胸部平扫质控模型。
     *
     * @param volumePath 服务器本地 NIfTI 体数据绝对路径
     * @return 推理结果；若调用失败，返回包含 error 字段的结果映射
     */
    Map<String, Object> analyzeChestNonContrast(String volumePath);

    /**
     * 调用 CT 胸部增强质控分析链路。
     *
     * @param inputPath 服务器本地影像路径，支持 DICOM/NIfTI/ZIP
     * @param metadata 任务附加元数据，如流速、造影剂总量等
     * @return 推理结果；若调用失败，返回包含 error 字段的结果映射
     */
    Map<String, Object> analyzeChestContrast(String inputPath, Map<String, Object> metadata);

    /**
     * 调用冠脉 CTA 质控分析链路。
     *
     * @param inputPath 服务器本地影像路径，支持 DICOM/NIfTI/ZIP
     * @param metadata 任务附加元数据，如心率、重建相位等
     * @return 推理结果；若调用失败，返回包含 error 字段的结果映射
     */
    Map<String, Object> analyzeCoronaryCta(String inputPath, Map<String, Object> metadata);
}

