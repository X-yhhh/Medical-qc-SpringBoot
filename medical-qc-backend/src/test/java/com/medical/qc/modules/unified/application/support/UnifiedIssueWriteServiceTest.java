package com.medical.qc.modules.unified.application.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medical.qc.modules.auth.persistence.mapper.UserMapper;
import com.medical.qc.modules.qcrule.application.QcRuleConfigServiceImpl;
import com.medical.qc.modules.unified.persistence.entity.UnifiedIssueActionLog;
import com.medical.qc.modules.unified.persistence.entity.UnifiedIssueTicket;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResult;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcTask;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedIssueActionLogMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedIssueCapaRecordMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedIssueTicketMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcResultMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcTaskMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedStudyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedIssueWriteServiceTest {

    @Mock
    private UnifiedIssueTicketMapper unifiedIssueTicketMapper;
    @Mock
    private UnifiedIssueActionLogMapper unifiedIssueActionLogMapper;
    @Mock
    private UnifiedIssueCapaRecordMapper unifiedIssueCapaRecordMapper;
    @Mock
    private UnifiedQcTaskMapper unifiedQcTaskMapper;
    @Mock
    private UnifiedQcResultMapper unifiedQcResultMapper;
    @Mock
    private UnifiedStudyMapper unifiedStudyMapper;
    @Mock
    private QcRuleConfigServiceImpl qcRuleConfigService;
    @Mock
    private UserMapper userMapper;

    private UnifiedIssueWriteService unifiedIssueWriteService;

    @BeforeEach
    void setUp() {
        unifiedIssueWriteService = new UnifiedIssueWriteService(
                unifiedIssueTicketMapper,
                unifiedIssueActionLogMapper,
                unifiedIssueCapaRecordMapper,
                unifiedQcTaskMapper,
                unifiedQcResultMapper,
                unifiedStudyMapper,
                qcRuleConfigService,
                userMapper);
    }

    @Test
    void shouldAutoResolveExistingIssueWhenQualityTaskBecomesNormal() {
        UnifiedQcTask task = new UnifiedQcTask();
        task.setId(140L);
        task.setSubmittedBy(23L);
        task.setTaskTypeCode("chest-contrast");

        UnifiedQcResult result = new UnifiedQcResult();
        result.setId(501L);
        result.setQcStatus("合格");
        result.setAbnormalCount(0);
        result.setQualityScore(BigDecimal.valueOf(100));
        result.setUpdatedAt(LocalDateTime.of(2026, 3, 16, 10, 0));

        UnifiedIssueTicket ticket = new UnifiedIssueTicket();
        ticket.setId(132L);
        ticket.setTaskId(140L);
        ticket.setStatus("待处理");

        when(unifiedQcTaskMapper.selectById(140L)).thenReturn(task);
        when(unifiedQcResultMapper.selectOne(any(QueryWrapper.class))).thenReturn(result);
        when(unifiedIssueTicketMapper.selectOne(any(QueryWrapper.class))).thenReturn(ticket);

        unifiedIssueWriteService.syncQualityTaskIssue(140L);

        ArgumentCaptor<UnifiedIssueTicket> ticketCaptor = ArgumentCaptor.forClass(UnifiedIssueTicket.class);
        verify(unifiedIssueTicketMapper).updateById(ticketCaptor.capture());
        UnifiedIssueTicket updatedTicket = ticketCaptor.getValue();
        assertEquals("已解决", updatedTicket.getStatus());
        assertEquals(501L, updatedTicket.getResultId());
        assertNotNull(updatedTicket.getResolvedAt());

        ArgumentCaptor<UnifiedIssueActionLog> logCaptor = ArgumentCaptor.forClass(UnifiedIssueActionLog.class);
        verify(unifiedIssueActionLogMapper).insert(logCaptor.capture());
        assertEquals("auto_resolve", logCaptor.getValue().getActionType());
        assertEquals("已解决", logCaptor.getValue().getAfterStatus());
    }

    @Test
    void shouldNotCreateOrCloseIssueWhenNoExistingTicketAndResultIsNormal() {
        UnifiedQcTask task = new UnifiedQcTask();
        task.setId(150L);
        task.setSubmittedBy(23L);
        task.setTaskTypeCode("head");

        UnifiedQcResult result = new UnifiedQcResult();
        result.setId(601L);
        result.setQcStatus("合格");
        result.setAbnormalCount(0);
        result.setQualityScore(BigDecimal.valueOf(100));

        when(unifiedQcTaskMapper.selectById(150L)).thenReturn(task);
        when(unifiedQcResultMapper.selectOne(any(QueryWrapper.class))).thenReturn(result);
        when(unifiedIssueTicketMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        unifiedIssueWriteService.syncQualityTaskIssue(150L);

        verify(unifiedIssueTicketMapper, never()).insert(any(UnifiedIssueTicket.class));
        verify(unifiedIssueTicketMapper, never()).updateById(any(UnifiedIssueTicket.class));
        verify(unifiedIssueActionLogMapper, never()).insert(any(UnifiedIssueActionLog.class));
    }
}
