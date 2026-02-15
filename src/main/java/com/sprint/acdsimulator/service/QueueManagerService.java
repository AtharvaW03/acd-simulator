package com.sprint.acdsimulator.service;

import com.sprint.acdsimulator.Call;
import org.springframework.stereotype.Service;

import java.util.concurrent.PriorityBlockingQueue;

@Service
public class QueueManagerService {

    // core data structure.
    // it's thread safe and automatically sorts based on 'compareTo' in Call.java
    private final PriorityBlockingQueue<Call> callQueue = new PriorityBlockingQueue<>();

    // Producer: this adds a call to the line
    public void addCall(Call call){
        System.out.println("--> [Queue] Incoming: " + call.getId() + ("Priority: " + call.getPriority() + ")"));
        callQueue.put(call);
    }

    // Consumer: Agents call this to get work
    // .take() waits if the queue is empty, so agents don't waste cpu
    public Call getNextCall() throws InterruptedException{
        return callQueue.take();
    }

    // helper fn to see how busy we are
    public int getQueueSize(){
        return callQueue.size();
    }
}
