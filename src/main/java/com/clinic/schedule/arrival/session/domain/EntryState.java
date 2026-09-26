package com.clinic.schedule.arrival.session.domain;

/** Where a patient stands in a session (D-63). Only a waiting one is in the order to be called. */
public enum EntryState {

    /** In the order, to be called. */
    WAITING,

    /** A practitioner called them, and is seeing them. */
    CALLED,

    /** The practitioner who called them finished. */
    SEEN,

    /** Reception says they left without being seen. */
    LEFT,

    /** Still waiting when the queue closed. */
    NOT_SEEN,

    /** Called time after time without coming in, and taken out of the queue. */
    DID_NOT_SHOW_UP
}
