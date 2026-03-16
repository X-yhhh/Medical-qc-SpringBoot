package com.medical.qc.modules.qctask.application.command;

import java.util.List;

/**
 * 历史质控任务结果修复命令。
 *
 * @param taskIds 指定要修复的任务编号；为空时修复全部历史任务
 * @param refreshIssues 是否在修复后同步刷新异常工单
 */
public record QualityTaskRepairCommand(List<String> taskIds, Boolean refreshIssues) {
}
