package com.medical.qc.modules.pacs.application;

import com.medical.qc.modules.pacs.model.PacsStudyCache;
import com.medical.qc.modules.pacs.application.query.PacsStudySearchQuery;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * PACS 查询应用服务。
 *
 * <p>当前作为 PACS 检索能力的模块化入口，
 * 后续可在此层对接真实 PACS 适配器与查询模型。</p>
 */
@Service
public class PacsQueryApplicationService {
    private final PacsServiceImpl pacsService;

    public PacsQueryApplicationService(PacsServiceImpl pacsService) {
        this.pacsService = pacsService;
    }

    public List<PacsStudyCache> searchStudies(PacsStudySearchQuery query) {
        return pacsService.searchStudies(
                query.taskType(),
                query.patientId(),
                query.patientName(),
                query.accessionNumber(),
                query.startDate(),
                query.endDate());
    }
}

