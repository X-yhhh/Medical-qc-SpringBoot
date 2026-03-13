package com.medical.qc.modules.qcresult.application;

import com.medical.qc.modules.qcresult.model.HemorrhageRecord;
import com.medical.qc.modules.qcresult.application.command.HemorrhageAnalysisCommand;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 脑出血质控结果应用服务。
 *
 * <p>当前先作为 controller 与旧业务服务之间的模块化入口，
 * 后续可在此层承接结果编排、DTO 转换和事务边界。</p>
 */
@Service
public class HemorrhageRecordApplicationService {
    private final QualityServiceImpl qualityService;

    public HemorrhageRecordApplicationService(QualityServiceImpl qualityService) {
        this.qualityService = qualityService;
    }

    public List<HemorrhageRecord> getHistory(Long userId, Integer limit) {
        return qualityService.getHistory(userId, limit);
    }

    public HemorrhageRecord getHistoryDetail(Long userId, Long recordId) {
        return qualityService.getHistoryRecord(userId, recordId);
    }

    public Map<String, Object> analyzeHemorrhage(HemorrhageAnalysisCommand command) throws IOException {
        return qualityService.processHemorrhage(
                command.file(),
                command.user(),
                command.patientName(),
                command.patientCode(),
                command.examId(),
                command.gender(),
                command.age(),
                command.studyDate(),
                command.sourceMode());
    }
}

