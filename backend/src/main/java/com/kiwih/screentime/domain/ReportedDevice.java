package com.kiwih.screentime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** What one device reported for a week, as entered during the weekly check. */
@Embeddable
public class ReportedDevice {

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private int minutes;

    protected ReportedDevice() {
    }

    public ReportedDevice(Long deviceId, int minutes) {
        this.deviceId = deviceId;
        this.minutes = minutes;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public int getMinutes() {
        return minutes;
    }
}
