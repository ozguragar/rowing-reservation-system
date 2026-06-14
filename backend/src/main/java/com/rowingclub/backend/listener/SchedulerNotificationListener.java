package com.rowingclub.backend.listener;

import com.rowingclub.backend.entity.NotificationLog;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.event.SchedulerCompletedEvent;
import com.rowingclub.backend.repository.NotificationLogRepository;
import com.rowingclub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchedulerNotificationListener {

    private final NotificationLogRepository notificationLogRepository;
    private final UserRepository userRepository;

    @Async
    @EventListener
    public void handleSchedulerCompleted(SchedulerCompletedEvent event) {
        log.info("Processing {} auto-scheduler assignments for notifications", event.getAssignments().size());

        for (Map<String, Object> assignment : event.getAssignments()) {
            Long userId = (Long) assignment.get("userId");
            String boatName = (String) assignment.get("boatName");
            String sessionInfo = (String) assignment.get("sessionInfo");

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) continue;

            NotificationLog notification = NotificationLog.builder()
                    .user(user)
                    .subject("Auto-Scheduler Assignment")
                    .message("You have been automatically assigned to boat '" + boatName +
                            "' for session on " + sessionInfo +
                            ". This is a simulated email notification.")
                    .build();

            notificationLogRepository.save(notification);
            log.info("Notification created for user {} - boat {}", user.getEmail(), boatName);
        }
    }
}
