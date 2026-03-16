package com.medical.qc.modules.qctask.application.command;

import java.util.List;

/**
 * 批量重跑命令。
 */
public record QualityTaskBatchRetryCommand(
        List<String> taskIds) {
}
