package com.medical.qc.modules.qcresult.application.support;

import com.medical.qc.modules.qcresult.model.HemorrhageRecord;
import com.medical.qc.modules.auth.persistence.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HemorrhageResultAssemblerTest {

    @Test
    void buildRecordShouldMapPredictionPayloadToHemorrhageRecord() {
        HemorrhageResultAssembler assembler = new HemorrhageResultAssembler();
        User user = new User();
        user.setId(9L);

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

        HemorrhageRecord record = new HemorrhageRecord();
        record.setPrediction("出血");
        record.setPrimaryIssue("脑出血");
        record.setQcStatus("不合格");
        record.setPatientCode("P-001");
        record.setGender("男");
        record.setAge(45);
        record.setStudyDate(LocalDate.of(2026, 3, 12));

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

        assertThat(response.get("prediction")).isEqualTo("出血");
        assertThat(response.get("primary_issue")).isEqualTo("脑出血");
        assertThat(response.get("qc_status")).isEqualTo("不合格");
        assertThat(response.get("source_mode")).isEqualTo("pacs");
        assertThat(response.get("model_name")).isEqualTo("AdvancedHemorrhageModel");
        assertThat(response.get("scanner_model")).isEqualTo("GE Revolution");
    }
}
