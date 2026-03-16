package com.medical.qc.modules.qctask.application.support;

/**
 * 轻量 NIfTI 采样统计结果。
 */
public record NiftiSampleStats(
        int width,
        int height,
        int depth,
        double spacingX,
        double spacingY,
        double spacingZ,
        long sampledVoxelCount,
        double meanHu,
        double stdHu,
        double maxHu,
        double highDensityRatio,
        double meanAbsoluteSliceShift) {
}
