package com.sprint.acdsimulator;

import com.sprint.acdsimulator.service.QueueManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CallController {

    private final QueueManagerService queueManager;

    @GetMapping("/call")
    public String receiveCall(@RequestParam String id, @RequestParam(defaultValue = "5") int priority){

        Call newCall = new Call(id, priority);
        queueManager.addCall(newCall);

        return "Call accepted from " + id + ". Queue size: " + queueManager.getQueueSize();
    }

}
