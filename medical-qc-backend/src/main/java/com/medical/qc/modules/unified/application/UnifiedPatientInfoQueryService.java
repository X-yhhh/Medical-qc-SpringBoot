package com.medical.qc.modules.unified.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medical.qc.modules.patient.model.QualityPatientInfo;
import com.medical.qc.modules.unified.persistence.entity.UnifiedPatient;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudy;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudyFile;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedPatientMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedStudyFileMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedStudyMapper;
import com.medical.qc.support.QualityPatientTaskSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统一模型患者信息查询服务。
 *
 * <p>当前用于在不切换写路径的前提下，为患者信息模块提供可选的新表读能力。</p>
 */
@Service
public class UnifiedPatientInfoQueryService {
    // 统一患者、检查和预览图分别来自三张表。
    private final UnifiedPatientMapper unifiedPatientMapper;
    private final UnifiedStudyMapper unifiedStudyMapper;
    private final UnifiedStudyFileMapper unifiedStudyFileMapper;

    public UnifiedPatientInfoQueryService(UnifiedPatientMapper unifiedPatientMapper,
                                          UnifiedStudyMapper unifiedStudyMapper,
                                          UnifiedStudyFileMapper unifiedStudyFileMapper) {
        this.unifiedPatientMapper = unifiedPatientMapper;
        this.unifiedStudyMapper = unifiedStudyMapper;
        this.unifiedStudyFileMapper = unifiedStudyFileMapper;
    }

    /**
     * 分页查询患者列表。
     * 数据链路：taskType -> studies 范围过滤 -> patients 条件过滤 -> preview 文件补齐 -> QualityPatientInfo 列表。
     */
    public Map<String, Object> getPatientPage(String taskType,
                                              String keyword,
                                              String patientId,
                                              String patientName,
                                              String accessionNumber,
                                              Integer page,
                                              Integer limit) {
        // 先归一化 taskType 与分页参数，避免前端传入异常值。
        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        int normalizedPage = page == null || page <= 0 ? 1 : page;
        int normalizedLimit = limit == null || limit <= 0 ? 10 : Math.min(limit, 50);

        List<UnifiedStudy> scopedStudies = listStudiesByTaskType(normalizedTaskType).stream()
                .filter(study -> matchesStudy(study, accessionNumber))
                .toList();
        Set<Long> patientIds = scopedStudies.stream()
                .map(UnifiedStudy::getPatientId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (patientIds.isEmpty()) {
            return buildResponse(normalizedTaskType, normalizedPage, normalizedLimit, List.of());
        }

        Map<Long, UnifiedPatient> patientMap = unifiedPatientMapper.selectBatchIds(patientIds).stream()
                .filter(patient -> matchesPatient(patient, keyword, patientId, patientName))
                .collect(Collectors.toMap(UnifiedPatient::getId, patient -> patient));

        // 最终列表按检查日期倒序，日期相同时再按主键倒序稳定排序。
        List<UnifiedStudy> filteredStudies = scopedStudies.stream()
                .filter(study -> patientMap.containsKey(study.getPatientId()))
                .sorted((left, right) -> {
                    LocalDate leftDate = left.getStudyDate();
                    LocalDate rightDate = right.getStudyDate();
                    if (leftDate == null && rightDate == null) {
                        return right.getId().compareTo(left.getId());
                    }
                    if (leftDate == null) {
                        return 1;
                    }
                    if (rightDate == null) {
                        return -1;
                    }
                    int compare = rightDate.compareTo(leftDate);
                    return compare != 0 ? compare : right.getId().compareTo(left.getId());
                })
                .toList();

        int total = filteredStudies.size();
        int fromIndex = Math.min((normalizedPage - 1) * normalizedLimit, total);
        int toIndex = Math.min(fromIndex + normalizedLimit, total);
        List<UnifiedStudy> pageStudies = filteredStudies.subList(fromIndex, toIndex);

        List<Long> studyIds = pageStudies.stream().map(UnifiedStudy::getId).toList();
        Map<Long, UnifiedStudyFile> previewFileMap = loadPrimaryPreviewFiles(studyIds);

        List<QualityPatientInfo> data = pageStudies.stream()
                .map(study -> toPatientInfo(patientMap.get(study.getPatientId()), study, previewFileMap.get(study.getId())))
                .toList();

        return buildResponse(normalizedTaskType, normalizedPage, normalizedLimit, data, total);
    }

    /**
     * 根据检查号查询患者信息。
     */
    public QualityPatientInfo getByAccessionNumber(String taskType, String accessionNumber) {
        String normalizedAccessionNumber = normalizeText(accessionNumber);
        if (normalizedAccessionNumber == null) {
            return null;
        }

        UnifiedStudy study = listStudiesByTaskType(QualityPatientTaskSupport.normalizeTaskType(taskType)).stream()
                .filter(item -> normalizedAccessionNumber.equals(item.getAccessionNumber()))
                .findFirst()
                .orElse(null);
        if (study == null) {
            return null;
        }

        UnifiedPatient patient = unifiedPatientMapper.selectById(study.getPatientId());
        UnifiedStudyFile previewFile = loadPrimaryPreviewFiles(List.of(study.getId())).get(study.getId());
        return toPatientInfo(patient, study, previewFile);
    }

    /**
     * 根据 studyId 查询患者信息。
     */
    public QualityPatientInfo getByStudyId(Long studyId) {
        if (studyId == null) {
            return null;
        }

        UnifiedStudy study = unifiedStudyMapper.selectById(studyId);
        if (study == null) {
            return null;
        }

        UnifiedPatient patient = study.getPatientId() == null ? null : unifiedPatientMapper.selectById(study.getPatientId());
        UnifiedStudyFile previewFile = loadPrimaryPreviewFiles(List.of(studyId)).get(studyId);
        return toPatientInfo(patient, study, previewFile);
    }

    /**
     * 根据 taskType 限定 studies 范围。
     */
    private List<UnifiedStudy> listStudiesByTaskType(String taskType) {
        QueryWrapper<UnifiedStudy> queryWrapper = new QueryWrapper<>();
        switch (taskType) {
            case QualityPatientTaskSupport.TASK_TYPE_HEAD -> queryWrapper.and(wrapper ->
                    wrapper.likeRight("study_no", "rt-head-")
                            .or().likeRight("study_no", "legacy-head-"));
            case QualityPatientTaskSupport.TASK_TYPE_HEMORRHAGE -> queryWrapper.and(wrapper ->
                    wrapper.likeRight("study_no", "rt-hemorrhage-")
                            .or().likeRight("study_no", "legacy-hemorrhage-"));
            case QualityPatientTaskSupport.TASK_TYPE_CHEST_NON_CONTRAST -> queryWrapper.and(wrapper ->
                    wrapper.likeRight("study_no", "rt-chest-non-contrast-")
                            .or().likeRight("study_no", "legacy-chest-nc-"));
            case QualityPatientTaskSupport.TASK_TYPE_CHEST_CONTRAST -> queryWrapper.and(wrapper ->
                    wrapper.likeRight("study_no", "rt-chest-contrast-")
                            .or().likeRight("study_no", "legacy-chest-c-"));
            case QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA -> queryWrapper.and(wrapper ->
                    wrapper.likeRight("study_no", "rt-coronary-cta-")
                            .or().likeRight("study_no", "legacy-coronary-"));
            default -> queryWrapper.last("LIMIT 0");
        }

        return unifiedStudyMapper.selectList(queryWrapper);
    }

    /**
     * 加载指定 studies 的主预览图。
     */
    private Map<Long, UnifiedStudyFile> loadPrimaryPreviewFiles(List<Long> studyIds) {
        if (studyIds == null || studyIds.isEmpty()) {
            return Map.of();
        }

        return unifiedStudyFileMapper.selectList(new QueryWrapper<UnifiedStudyFile>()
                        .in("study_id", studyIds)
                        .eq("file_role", "PREVIEW")
                        .orderByDesc("is_primary")
                        .orderByDesc("updated_at"))
                .stream()
                .collect(Collectors.toMap(
                        UnifiedStudyFile::getStudyId,
                        item -> item,
                        (left, right) -> Boolean.TRUE.equals(left.getIsPrimary()) ? left : right));
    }

    /**
     * 患者维度筛选：关键字、患者 ID、患者姓名。
     */
    private boolean matchesPatient(UnifiedPatient patient,
                                   String keyword,
                                   String patientId,
                                   String patientName) {
        if (patient == null) {
            return false;
        }

        String normalizedKeyword = normalizeText(keyword);
        if (normalizedKeyword != null
                && !contains(patient.getPatientName(), normalizedKeyword)
                && !contains(patient.getPatientNo(), normalizedKeyword)) {
            return false;
        }

        String normalizedPatientId = normalizeText(patientId);
        if (normalizedPatientId != null && !normalizedPatientId.equals(patient.getPatientNo())) {
            return false;
        }

        String normalizedPatientName = normalizeText(patientName);
        return normalizedPatientName == null || contains(patient.getPatientName(), normalizedPatientName);
    }

    /**
     * 检查维度筛选：检查号。
     */
    private boolean matchesStudy(UnifiedStudy study, String accessionNumber) {
        String normalizedAccessionNumber = normalizeText(accessionNumber);
        return normalizedAccessionNumber == null || normalizedAccessionNumber.equals(study.getAccessionNumber());
    }

    /**
     * 把统一模型三张表的数据映射为旧前端仍在使用的 QualityPatientInfo。
     */
    private QualityPatientInfo toPatientInfo(UnifiedPatient patient,
                                             UnifiedStudy study,
                                             UnifiedStudyFile previewFile) {
        QualityPatientInfo item = new QualityPatientInfo();
        item.setId(study.getId());
        item.setPatientId(patient == null ? null : patient.getPatientNo());
        item.setPatientName(patient == null ? null : patient.getPatientName());
        item.setAccessionNumber(study.getAccessionNumber());
        item.setGender(patient == null ? null : patient.getGender());
        item.setAge(parseAge(patient == null ? null : patient.getAgeText()));
        item.setStudyDate(study.getStudyDate());
        item.setImagePath(previewFile == null ? null : firstNonBlank(previewFile.getPublicPath(), previewFile.getFilePath()));
        item.setRemark(patient == null ? null : patient.getRemark());
        item.setCreatedAt(study.getCreatedAt());
        item.setUpdatedAt(study.getUpdatedAt());
        return item;
    }

    /**
     * 构造空列表响应。
     */
    private Map<String, Object> buildResponse(String taskType,
                                              int page,
                                              int limit,
                                              List<QualityPatientInfo> data) {
        return buildResponse(taskType, page, limit, data, 0);
    }

    /**
     * 构造分页响应。
     */
    private Map<String, Object> buildResponse(String taskType,
                                              int page,
                                              int limit,
                                              List<QualityPatientInfo> data,
                                              long total) {
        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("total", total);

        Map<String, Object> summary = new HashMap<>();
        summary.put("taskType", taskType);
        summary.put("taskLabel", QualityPatientTaskSupport.resolveTaskLabel(taskType));
        summary.put("totalPatients", total);

        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        response.put("pagination", pagination);
        response.put("summary", summary);
        return response;
    }

    /**
     * 将年龄文本安全转回整数。
     */
    private Integer parseAge(String ageText) {
        String normalizedAgeText = normalizeText(ageText);
        if (normalizedAgeText == null) {
            return null;
        }
        try {
            return Integer.parseInt(normalizedAgeText);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 安全做包含判断。
     */
    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }

    /**
     * 从多个候选文本中取第一个非空值。
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalizedValue = normalizeText(value);
            if (normalizedValue != null) {
                return normalizedValue;
            }
        }
        return null;
    }

    /**
     * 去空格并把空字符串转为 null。
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

