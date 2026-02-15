package com.sprint.acdsimulator;

import com.sprint.acdsimulator.service.QueueManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentSimulationRunner implements CommandLineRunner {

    //CommandLineRunner tells spring to run this code automatically as soon as the app starts
    private final QueueManagerService queueManagerService;

    @Override
    public void run(String... args) throws Exception{
        //start 2 seperate threads which run in the background
        //this simulates 2 humans sitting at desks

        new Thread(() -> agentLoop("Agent_Alice")).start();
        new Thread(() -> agentLoop("Agent_Bob")).start();
    }

    private void agentLoop(String name){
        System.out.println(name + "is clocked in and waiting....");

        while(true){
            try{
                //ask for work: this line blocks until a call arrives
                Call call = queueManagerService.getNextCall();

                System.out.println("   [ " + name + " ] Answering call: " + call.getId() + "Priority: " + call.getPriority() + ")");

                //simulate a conversation
                Thread.sleep(2000);

                System.out.println("   [ " + name + " ] Finished call: " + call.getId());
            }catch (InterruptedException e){
                System.out.println(name + "shift ended");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

}
