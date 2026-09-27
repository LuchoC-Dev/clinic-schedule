package com.clinic.schedule.arrival.live;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Tells every open screen that a queue changed, as it happens (D-66), through Server-Sent Events:
 * one stream per screen, from the server to the browser, which is all a screen that only listens
 * needs.
 *
 * <p>It tells them once the change is committed, never before: a command that is refused leaves
 * nothing to show. The streams are kept in memory, since the application runs as one instance; a
 * stream that can no longer be written to is dropped, and the browser opens a new one by itself.
 */
@Component
public class LiveQueues {

    /** How long a stream stays open before the browser is asked to open a new one. */
    private static final Duration STREAM_LIFETIME = Duration.ofMinutes(30);

    private final Set<SseEmitter> screens = new CopyOnWriteArraySet<>();

    /** A new stream for a screen, told of every change from now on. */
    public SseEmitter subscribe() {
        SseEmitter screen = new SseEmitter(STREAM_LIFETIME.toMillis());
        screen.onCompletion(() -> screens.remove(screen));
        screen.onTimeout(() -> screens.remove(screen));
        screen.onError(error -> screens.remove(screen));
        screens.add(screen);
        return screen;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void queueChanged(QueueChanged changed) {
        for (SseEmitter screen : screens) {
            try {
                screen.send(SseEmitter.event().name("queue").data(changed.queueId().toString()));
            } catch (IOException | IllegalStateException e) {
                screens.remove(screen);
            }
        }
    }
}
