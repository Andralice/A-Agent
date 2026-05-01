package com.start.agent.qq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QqGroupAccessService {

    private final List<Long> allowedGroupIds;

    public QqGroupAccessService(@Value("${qq.allowed-groups:}") String allowedGroupsConfig) {
        if (allowedGroupsConfig == null || allowedGroupsConfig.trim().isEmpty()) {
            this.allowedGroupIds = null;
            log.warn("【系统启动】未配置允许的群号列表，将响应所有群的消息（存在安全风险）");
        } else {
            this.allowedGroupIds = Arrays.stream(allowedGroupsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            log.info("【系统启动】已加载群号白名单，共{}个授权群: {}", allowedGroupIds.size(), allowedGroupIds);
        }
    }

    public boolean isAllowed(long groupId) {
        return allowedGroupIds == null || allowedGroupIds.contains(groupId);
    }
}
