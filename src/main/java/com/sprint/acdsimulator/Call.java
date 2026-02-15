package com.sprint.acdsimulator;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Call implements Comparable<Call>{

    //'final' tells lombok to include this in generated constructor
    private final String id;
    private final int priority; // 1=High (VIP), 5=Low (Regular)

    //initialize this here so lombok won't ask for it in constructor
    private final LocalDateTime receivedAt = LocalDateTime.now();

    @Override
    public int compareTo(Call other){
        //we need this manual logic to tell Queue how to sort
        return Integer.compare(this.priority, other.priority);
    }
}
