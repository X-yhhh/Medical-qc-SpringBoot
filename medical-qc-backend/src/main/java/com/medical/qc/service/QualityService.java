package com.medical.qc.service;

import com.medical.qc.entity.HemorrhageRecord;
import com.medical.qc.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface QualityService {
    /**
     * 获取指定用户的全部脑出血检测历史记录。
     *
     * @param userId 用户 ID
     * @return 历史记录列表
     */
    List<HemorrhageRecord> getHistory(Long userId);

    /**
     * 获取指定用户最近的脑出血检测历史记录。
     *
     * @param userId 用户 ID
     * @param limit 返回数量上限
     * @return 历史记录列表
     */
    List<HemorrhageRecord> getHistory(Long userId, Integer limit);

    /**
     * ???????????????????
     *
     * @param userId   ?? ID
     * @param recordId ???? ID
     * @return ????????????? null
     */
    HemorrhageRecord getHistoryRecord(Long userId, Long recordId);

    Map<String, Object> processHemorrhage(MultipartFile file,
                                          User user,
                                          String patientName,
                                          String patientCode,
                                          String examId,
                                          String gender,
                                          Integer age,
                                          LocalDate studyDate,
                                          String sourceMode) throws IOException;
    
    // Mock methods for other items
    Map<String, Object> detectHead(MultipartFile file);
    Map<String, Object> detectChest(MultipartFile file, boolean contrast);
    Map<String, Object> detectCoronary(MultipartFile file);
}
