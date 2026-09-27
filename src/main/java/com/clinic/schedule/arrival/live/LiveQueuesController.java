package com.clinic.schedule.arrival.live;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The stream the queue screens listen to (D-66): an event named {@code queue} whose data is the id
 * of the queue that changed. For the screens, behind their login; reception and the practitioners
 * reach it, by route.
 */
@Controller
class LiveQueuesController {

    private final LiveQueues live;

    LiveQueuesController(LiveQueues live) {
        this.live = live;
    }

    @GetMapping(path = "/queue-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    SseEmitter events() {
        return live.subscribe();
    }

    /**
     * A screen that left - its page was reloaded or closed - is found out only when it is next
     * written to, and the stream then ends in this error. It is no fault: {@link LiveQueues} has
     * already dropped the stream, and there is no one left to answer.
     */
    @ExceptionHandler(IOException.class)
    void screenLeft() {
    }
}
