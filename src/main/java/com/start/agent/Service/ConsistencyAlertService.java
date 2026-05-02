package com.start.agent.service;

import com.start.agent.model.ConsistencyAlert;
import com.start.agent.repository.ConsistencyAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 一致性告警的持久化与查询。 */
@Service
public class ConsistencyAlertService {
    private final ConsistencyAlertRepository consistencyAlertRepository;

    public ConsistencyAlertService(ConsistencyAlertRepository consistencyAlertRepository) {
        this.consistencyAlertRepository = consistencyAlertRepository;
    }

    @Transactional
    public void saveAlert(Long novelId, Integer chapterNumber, String alertType, String severity,
                          String message, boolean attempted, boolean success) {
        ConsistencyAlert alert = new ConsistencyAlert();
        alert.setNovelId(novelId);
        alert.setChapterNumber(chapterNumber);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setAutoFixAttempted(attempted);
        alert.setAutoFixSuccess(success);
        consistencyAlertRepository.save(alert);
    }

    public List<ConsistencyAlert> getAlerts(Long novelId) {
        return consistencyAlertRepository.findByNovelIdOrderByCreateTimeDesc(novelId);
    }
}
