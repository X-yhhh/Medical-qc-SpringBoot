package com.medical.qc.service;

import com.medical.qc.entity.HemorrhageRecord;
import com.medical.qc.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface QualityService {
    List<HemorrhageRecord> getHistory(Long userId);
    Map<String, Object> processHemorrhage(MultipartFile file, User user) throws IOException;
    
    // Mock methods for other items
    Map<String, Object> detectHead(MultipartFile file);
    Map<String, Object> detectChest(MultipartFile file, boolean contrast);
    Map<String, Object> detectCoronary(MultipartFile file);
}
