package com.medical.qc.modules.qcresult.application.support;

import com.medical.qc.modules.qcresult.model.HemorrhageRecord;
import com.medical.qc.modules.auth.persistence.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HemorrhageResultAssembler 单元测试。
 * 重点校验模型输出到 HemorrhageRecord 与前端响应字段的映射关系。
 */
class HemorrhageResultAssemblerTest {

    @Test
    void buildRecordShouldMapPredictionPayloadToHemorrhageRecord() {
        HemorrhageResultAssembler assembler = new HemorrhageResultAssembler();
        // 用户 ID 需要透传到检测记录，便于后续按用户查询历史。
        User user = new User();
        user.setId(9L);

        // 构造预处理上下文，模拟本地上传的检测输入。
        HemorrhagePreparedContext context = new HemorrhagePreparedContext(
                "local",
                "C:/uploads/demo.png",
                "uploads/demo.png",
                "张三",
                "P-001",
                "男",
                45,
                LocalDate.of(2026, 3, 12),
                "Siemens SOMATOM");

        // 模拟 Python 模型返回的关键预测字段。
        Map<String, Object> prediction = new HashMap<>();
        prediction.put("prediction", "Hemorrhage");
        prediction.put("confidence_level", "高");
        prediction.put("hemorrhage_probability", 92.5D);
        prediction.put("no_hemorrhage_probability", 7.5D);
        prediction.put("analysis_duration", 1.2D);
        prediction.put("midline_shift", true);
        prediction.put("shift_score", 0.8D);
        prediction.put("midline_detail", "中线明显偏移");
        prediction.put("ventricle_issue", true);
        prediction.put("ventricle_detail", "脑室受压变形");

        HemorrhageRecord record = assembler.buildRecord(user, "EX-1001", context, prediction);

        // 断言记录字段已按业务语义完成映射和中文转换。
        assertThat(record.getUserId()).isEqualTo(9L);
        assertThat(record.getPatientName()).isEqualTo("张三");
        assertThat(record.getPatientCode()).isEqualTo("P-001");
        assertThat(record.getExamId()).isEqualTo("EX-1001");
        assertThat(record.getPrediction()).isEqualTo("出血");
        assertThat(record.getQcStatus()).isEqualTo("不合格");
        assertThat(record.getPrimaryIssue()).isEqualTo("脑出血");
        assertThat(record.getDevice()).isEqualTo("cuda");
    }

    @Test
    void enrichResponseShouldAppendFrontendFields() {
        HemorrhageResultAssembler assembler = new HemorrhageResultAssembler();

        // 构造已组装的记录实体，模拟写库前的数据状态。
        HemorrhageRecord record = new HemorrhageRecord();
        record.setPrediction("出血");
        record.setPrimaryIssue("脑出血");
        record.setQcStatus("不合格");
        record.setPatientCode("P-001");
        record.setGender("男");
        record.setAge(45);
        record.setStudyDate(LocalDate.of(2026, 3, 12));

        // 构造 PACS 来源上下文，验证 source_mode、scanner_model 等字段拼装。
        HemorrhagePreparedContext context = new HemorrhagePreparedContext(
                "pacs",
                "C:/uploads/demo.png",
                "uploads/pacs/demo.png",
                "张三",
                "P-001",
                "男",
                45,
                LocalDate.of(2026, 3, 12),
                "GE Revolution");

        Map<String, Object> response = new HashMap<>();
        assembler.enrichResponse(response, record, context);

        // 前端依赖这些字段直接回显详情页和结果页状态。
        assertThat(response.get("prediction")).isEqualTo("出血");
        assertThat(response.get("primary_issue")).isEqualTo("脑出血");
        assertThat(response.get("qc_status")).isEqualTo("不合格");
        assertThat(response.get("source_mode")).isEqualTo("pacs");
        assertThat(response.get("model_name")).isEqualTo("AdvancedHemorrhageModel");
        assertThat(response.get("scanner_model")).isEqualTo("GE Revolution");
        assertThat(response.get("mock")).isEqualTo(false);
        assertThat(response.get("analysisMode")).isEqualTo("real-model");
        assertThat(response.get("analysisLabel")).isEqualTo("真实模型推理");
        @SuppressWarnings("unchecked")
        var qcItems = (java.util.List<Map<String, Object>>) response.get("qcItems");
        assertThat(qcItems).hasSize(3);
        assertThat(response.get("summary")).isInstanceOf(Map.class);
    }
}
