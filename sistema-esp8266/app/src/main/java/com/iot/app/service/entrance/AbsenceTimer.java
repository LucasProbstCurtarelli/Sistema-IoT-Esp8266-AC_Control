package com.iot.app.service.entrance;

/**
 * Single absence deadline. A second arm while the first is still running does not restart it.
 */
public interface AbsenceTimer {

    /**
     * @return true when this call started the deadline, false when one was already running
     */
    boolean arm(Runnable onFire);

    void cancel();

    boolean isArmed();
}
