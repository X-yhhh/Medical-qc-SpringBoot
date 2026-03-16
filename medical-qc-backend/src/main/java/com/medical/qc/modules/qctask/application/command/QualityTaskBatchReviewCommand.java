package com.medical.qc.modules.qctask.application.command;

import java.util.List;

/**
 * 批量人工复核命令。
 */
public record QualityTaskBatchReviewCommand(
        List<String> taskIds,
        String reviewStatus,
        String reviewComment,
        Boolean lockResult) {
}
