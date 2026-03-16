package com.medical.qc.modules.qctask.application.support;

import com.medical.qc.modules.qcresult.model.HemorrhageRecord;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质控任务报告生成服务。
 */
@Service
public class QualityTaskReportService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String FONT_FAMILY = "Microsoft YaHei";
    private static final String COLOR_PRIMARY = "1F4E78";
    private static final String COLOR_TEXT = "1F2937";
    private static final String COLOR_MUTED = "6B7280";
    private static final String COLOR_TABLE_BORDER = "D1D5DB";
    private static final String COLOR_TABLE_HEADER = "DCE6F1";
    private static final String COLOR_LABEL_CELL = "F3F4F6";

    /**
     * 生成单条任务的 DOCX 报告。
     */
    @SuppressWarnings("unchecked")
    public byte[] buildTaskReportDocx(Map<String, Object> taskDetail) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            configureDocument(document);
            addTitle(document,
                    String.valueOf(taskDetail.getOrDefault("taskTypeName", "影像质控报告")),
                    "由系统自动生成的结构化质控报告");
            addOverviewTable(document, taskDetail);

            Map<String, Object> result = taskDetail.get("result") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
            Map<String, Object> patientInfo = result.get("patientInfo") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
            addKeyValueSection(document, "患者与采集信息", humanizePatientInfo(patientInfo));

            List<Map<String, Object>> qcItems = result.get("qcItems") instanceof List<?> list
                    ? (List<Map<String, Object>>) list
                    : List.of();
            addQcItemsTable(document, qcItems);

            List<Map<String, Object>> auditLogs = taskDetail.get("auditLogs") instanceof List<?> list
                    ? (List<Map<String, Object>>) list
                    : List.of();
            addAuditTable(document, auditLogs);

            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * 生成多条任务的 CSV 摘要。
     */
    public byte[] buildTaskCsv(List<Map<String, Object>> taskItems) {
        List<String> rows = new ArrayList<>();
        rows.add("\uFEFF任务ID,任务类型,患者姓名,检查号,执行状态,质控结论,质控分,异常项,复核状态,设备,提交时间,完成时间");
        for (Map<String, Object> item : taskItems) {
            rows.add(String.join(",",
                    csv(item.get("taskId")),
                    csv(item.get("taskTypeName")),
                    csv(item.get("patientName")),
                    csv(item.get("examId")),
                    csv(item.get("status")),
                    csv(item.get("qcStatus")),
                    csv(item.get("qualityScore")),
                    csv(item.get("abnormalCount")),
                    csv(item.get("reviewStatus")),
                    csv(item.get("device")),
                    csv(item.get("submittedAt")),
                    csv(item.get("completedAt"))));
        }
        return String.join("\n", rows).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成脑出血单例检测报告。
     */
    public byte[] buildHemorrhageReportDocx(HemorrhageRecord record) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            configureDocument(document);
            addTitle(document, "头部出血 AI 智能检测", "脑出血辅助检测正式报告");

            XWPFTable overviewTable = createStyledKeyValueTable(document, 7);
            fillRow(overviewTable, 0, "记录 ID", record == null ? null : record.getId());
            fillRow(overviewTable, 1, "患者姓名", record == null ? null : record.getPatientName());
            fillRow(overviewTable, 2, "患者编号", record == null ? null : record.getPatientCode());
            fillRow(overviewTable, 3, "检查编号", record == null ? null : record.getExamId());
            fillRow(overviewTable, 4, "检测结论", record == null ? null : record.getQcStatus());
            fillRow(overviewTable, 5, "AI 判定", record == null ? null : record.getPrediction());
            fillRow(overviewTable, 6, "检测时间", record == null ? null : record.getCreatedAt());

            addSectionTitle(document, "关键指标");

            XWPFTable metricsTable = createStyledKeyValueTable(document, 6);
            fillRow(metricsTable, 0, "出血风险", probabilityText(record == null ? null : record.getHemorrhageProbability()));
            fillRow(metricsTable, 1, "非出血概率", probabilityText(record == null ? null : record.getNoHemorrhageProbability()));
            fillRow(metricsTable, 2, "置信度", record == null ? null : record.getConfidenceLevel());
            fillRow(metricsTable, 3, "主要异常", record == null ? null : record.getPrimaryIssue());
            fillRow(metricsTable, 4, "中线偏移", record == null ? null : record.getMidlineDetail());
            fillRow(metricsTable, 5, "脑室结构", record == null ? null : record.getVentricleDetail());

            addSectionTitle(document, "采集与推理信息");

            XWPFTable deviceTable = createStyledKeyValueTable(document, 4);
            fillRow(deviceTable, 0, "性别 / 年龄", record == null ? null : (safe(record.getGender()) + " / " + safe(record.getAge())));
            fillRow(deviceTable, 1, "检查日期", record == null ? null : record.getStudyDate());
            fillRow(deviceTable, 2, "设备型号", record == null ? null : record.getDevice());
            fillRow(deviceTable, 3, "分析耗时", record == null ? null : safe(record.getAnalysisDuration()) + " ms");

            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * 生成异常汇总 CSV。
     */
    public byte[] buildIssueCsv(List<Map<String, Object>> issueItems) {
        List<String> rows = new ArrayList<>();
        rows.add("\uFEFF异常ID,发现时间,患者姓名,检查编号,检查类型,主异常项,异常描述,优先级,责任角色,SLA截止,状态");
        for (Map<String, Object> item : issueItems) {
            rows.add(String.join(",",
                    csv(item.get("id")),
                    csv(item.get("date")),
                    csv(item.get("patientName")),
                    csv(item.get("examId")),
                    csv(item.get("type")),
                    csv(item.get("issueType")),
                    csv(item.get("description")),
                    csv(item.get("priority")),
                    csv(item.get("responsibleRoleLabel")),
                    csv(item.get("dueAt")),
                    csv(item.get("status"))));
        }
        return String.join("\n", rows).getBytes(StandardCharsets.UTF_8);
    }

    private void configureDocument(XWPFDocument document) {
        document.getDocument().getBody().addNewSectPr();
    }

    private void addTitle(XWPFDocument document, String title, String subtitle) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(120);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(18);
        run.setFontFamily(FONT_FAMILY);
        run.setColor(COLOR_PRIMARY);
        run.setText(title + "报告");

        XWPFParagraph subtitleParagraph = document.createParagraph();
        subtitleParagraph.setAlignment(ParagraphAlignment.CENTER);
        subtitleParagraph.setSpacingAfter(180);
        XWPFRun subtitleRun = subtitleParagraph.createRun();
        subtitleRun.setFontFamily(FONT_FAMILY);
        subtitleRun.setFontSize(10);
        subtitleRun.setColor(COLOR_MUTED);
        subtitleRun.setText(subtitle + " · 生成时间：" + DATE_TIME_FORMATTER.format(LocalDateTime.now()));
        subtitleRun.setUnderline(UnderlinePatterns.NONE);
    }

    private void addOverviewTable(XWPFDocument document, Map<String, Object> taskDetail) {
        XWPFTable table = createStyledKeyValueTable(document, 5);
        fillRow(table, 0, "任务 ID", taskDetail.get("taskId"));
        fillRow(table, 1, "任务类型", taskDetail.get("taskTypeName"));
        fillRow(table, 2, "执行状态", taskDetail.get("status"));
        fillRow(table, 3, "质控结论", taskDetail.get("qcStatus"));
        fillRow(table, 4, "复核状态", taskDetail.get("reviewStatus"));
    }

    private void addKeyValueSection(XWPFDocument document, String title, Map<String, Object> values) {
        addSectionTitle(document, title);

        XWPFTable table = createStyledKeyValueTable(document, Math.max(values.size(), 1));
        if (values.isEmpty()) {
            fillRow(table, 0, "说明", "暂无数据");
            return;
        }
        int rowIndex = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            fillRow(table, rowIndex++, entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private void addQcItemsTable(XWPFDocument document, List<Map<String, Object>> qcItems) {
        addSectionTitle(document, "质控项明细");

        XWPFTable table = createStyledMatrixTable(document, Math.max(qcItems.size() + 1, 2), 4);
        fillHeaderRow(table.getRow(0), "质控项", "状态", "描述", "详情");
        if (qcItems.isEmpty()) {
            fillRow(table, 1, "暂无明细", "--", "--", "--");
            return;
        }
        for (int index = 0; index < qcItems.size(); index++) {
            Map<String, Object> item = qcItems.get(index);
            fillRow(table, index + 1,
                    item.get("name"),
                    item.get("status"),
                    item.get("description"),
                    item.get("detail"));
        }
    }

    private void addAuditTable(XWPFDocument document, List<Map<String, Object>> auditLogs) {
        addSectionTitle(document, "审计记录");

        XWPFTable table = createStyledMatrixTable(document, Math.max(auditLogs.size() + 1, 2), 4);
        fillHeaderRow(table.getRow(0), "时间", "动作", "操作人", "备注");
        if (auditLogs.isEmpty()) {
            fillRow(table, 1, "--", "--", "--", "暂无审计记录");
            return;
        }
        for (int index = 0; index < auditLogs.size(); index++) {
            Map<String, Object> item = auditLogs.get(index);
            fillRow(table, index + 1,
                    item.get("createdAt"),
                    item.get("actionType"),
                    item.get("operatorName"),
                    item.get("comment"));
        }
    }

    private void addSectionTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(160);
        paragraph.setSpacingAfter(100);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontFamily(FONT_FAMILY);
        run.setFontSize(12);
        run.setColor(COLOR_PRIMARY);
        run.setText(title);
    }

    private XWPFTable createStyledKeyValueTable(XWPFDocument document, int rowCount) {
        XWPFTable table = document.createTable(rowCount, 2);
        styleTable(table);
        return table;
    }

    private XWPFTable createStyledMatrixTable(XWPFDocument document, int rowCount, int columnCount) {
        XWPFTable table = document.createTable(rowCount, columnCount);
        styleTable(table);
        return table;
    }

    private void styleTable(XWPFTable table) {
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 1, 0, COLOR_TABLE_BORDER);
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 1, 0, COLOR_TABLE_BORDER);
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 1, 0, COLOR_TABLE_BORDER);
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 1, 0, COLOR_TABLE_BORDER);
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 1, 0, COLOR_TABLE_BORDER);
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 1, 0, COLOR_TABLE_BORDER);
    }

    private void fillHeaderRow(XWPFTableRow row, Object first, Object second, Object third, Object fourth) {
        fillHeaderCell(row.getCell(0), first);
        fillHeaderCell(row.getCell(1), second);
        fillHeaderCell(row.getCell(2), third);
        fillHeaderCell(row.getCell(3), fourth);
    }

    private void fillRow(XWPFTable table, int rowIndex, Object first, Object second) {
        fillLabelCell(table.getRow(rowIndex).getCell(0), first);
        fillValueCell(table.getRow(rowIndex).getCell(1), second);
    }

    private void fillRow(XWPFTable table, int rowIndex, Object first, Object second, Object third, Object fourth) {
        fillValueCell(table.getRow(rowIndex).getCell(0), first);
        fillValueCell(table.getRow(rowIndex).getCell(1), second);
        fillValueCell(table.getRow(rowIndex).getCell(2), third);
        fillValueCell(table.getRow(rowIndex).getCell(3), fourth);
    }

    private String csv(Object value) {
        String text = String.valueOf(value == null ? "" : value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String probabilityText(Float probability) {
        if (probability == null) {
            return "--";
        }
        return String.format("%.1f%%", probability * 100.0F);
    }

    private String safe(Object value) {
        return value == null ? "--" : String.valueOf(value);
    }

    private void fillHeaderCell(XWPFTableCell cell, Object value) {
        setCellText(cell, value, COLOR_TABLE_HEADER, true);
    }

    private void fillLabelCell(XWPFTableCell cell, Object value) {
        setCellText(cell, value, COLOR_LABEL_CELL, true);
    }

    private void fillValueCell(XWPFTableCell cell, Object value) {
        setCellText(cell, value, "FFFFFF", false);
    }

    private void setCellText(XWPFTableCell cell, Object value, String backgroundColor, boolean bold) {
        cell.removeParagraph(0);
        cell.setColor(backgroundColor);
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setSpacingAfter(0);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily(FONT_FAMILY);
        run.setFontSize(10);
        run.setColor(COLOR_TEXT);
        run.setBold(bold);
        run.setText(safe(value));
    }

    private Map<String, Object> humanizePatientInfo(Map<String, Object> patientInfo) {
        Map<String, String> labelMap = Map.ofEntries(
                Map.entry("name", "姓名"),
                Map.entry("patientId", "患者编号"),
                Map.entry("gender", "性别"),
                Map.entry("age", "年龄"),
                Map.entry("studyId", "检查编号"),
                Map.entry("accessionNumber", "Accession No"),
                Map.entry("studyDate", "检查日期"),
                Map.entry("device", "设备"),
                Map.entry("sliceCount", "图像层数"),
                Map.entry("sliceThickness", "层厚"),
                Map.entry("pixelSpacing", "像素间距"),
                Map.entry("heartRate", "平均心率"),
                Map.entry("hrVariability", "心率波动"),
                Map.entry("reconPhase", "重建相位"),
                Map.entry("kVp", "管电压"),
                Map.entry("flowRate", "造影剂流速"),
                Map.entry("contrastVolume", "造影剂总量"),
                Map.entry("injectionSite", "注射部位"),
                Map.entry("bolusTrackingHu", "追踪阈值"),
                Map.entry("scanDelaySec", "扫描延迟"),
                Map.entry("sourceLabel", "来源"),
                Map.entry("originalFilename", "原始文件"));
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : patientInfo.entrySet()) {
            normalized.put(labelMap.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue());
        }
        return normalized;
    }
}
