package com.clinic.schedule.arrival.session.http;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.arrival.session.command.CallNextUseCase;
import com.clinic.schedule.arrival.session.command.DidNotComeInUseCase;
import com.clinic.schedule.arrival.session.command.FinishUseCase;
import com.clinic.schedule.arrival.session.command.NoQueueServedException;
import com.clinic.schedule.arrival.session.query.ViewMyQueueUseCase;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.practitioner.api.PractitionerReader;

/**
 * The practitioner's side of their queue (D-63): what it looks like, and the three things they do
 * — call the next patient, finish with them, or say they did not come in. The patient called is
 * always the one the order says.
 */
@RestController
@RequestMapping("/api/my-queue")
class MyQueueController {

    private final ViewMyQueueUseCase view;
    private final CallNextUseCase callNext;
    private final FinishUseCase finish;
    private final DidNotComeInUseCase didNotComeIn;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    MyQueueController(ViewMyQueueUseCase view, CallNextUseCase callNext, FinishUseCase finish,
            DidNotComeInUseCase didNotComeIn, PatientReader patients, PractitionerReader practitioners) {
        this.view = view;
        this.callNext = callNext;
        this.finish = finish;
        this.didNotComeIn = didNotComeIn;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    @GetMapping
    MyQueueResponse mine() {
        return view.handle().map(mine -> MyQueueResponse.of(mine, patients, practitioners)).orElseThrow(NoQueueServedException::new);
    }

    @PostMapping("/call-next")
    MyQueueResponse callNext() {
        callNext.handle();
        return mine();
    }

    @PostMapping("/finish")
    MyQueueResponse finish() {
        finish.handle();
        return mine();
    }

    @PostMapping("/did-not-come-in")
    MyQueueResponse didNotComeIn() {
        didNotComeIn.handle();
        return mine();
    }
}
