package com.medical.qc.modules.qctask.application.command;

/**
 * 单条质控任务人工复核命令。
 */
public record QualityTaskReviewCommand(
        String reviewStatus,
        String reviewComment,
        Boolean lockResult,
        String externalRef) {
}
