package com.rowingclub.backend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.util.List;
import java.util.Map;

@Getter
public class SchedulerCompletedEvent extends ApplicationEvent {

    private final List<Map<String, Object>> assignments;

    public SchedulerCompletedEvent(Object source, List<Map<String, Object>> assignments) {
        super(source);
        this.assignments = assignments;
    }
}
