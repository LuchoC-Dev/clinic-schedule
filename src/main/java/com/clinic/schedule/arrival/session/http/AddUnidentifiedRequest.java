package com.clinic.schedule.arrival.session.http;

/** Whether a patient who gives no name comes in as an emergency; the body may be left out. */
record AddUnidentifiedRequest(Boolean emergency) {

    boolean isEmergency() {
        return Boolean.TRUE.equals(emergency);
    }
}
