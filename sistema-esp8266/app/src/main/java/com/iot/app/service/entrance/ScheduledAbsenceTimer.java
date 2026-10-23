package com.iot.app.service.entrance;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

@Component
public class ScheduledAbsenceTimer implements AbsenceTimer {

    static final Duration ABSENCE = Duration.ofSeconds(5);

    private final TaskScheduler scheduler;
    private ScheduledFuture<?> future;

    public ScheduledAbsenceTimer(@Qualifier("entranceTaskScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public synchronized boolean arm(Runnable onFire) {
        if (future != null && !future.isDone()) {
            return false;
        }
        future = scheduler.schedule(onFire, Instant.now().plus(ABSENCE));
        return true;
    }

    @Override
    public synchronized void cancel() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    @Override
    public synchronized boolean isArmed() {
        return future != null && !future.isDone();
    }
}
