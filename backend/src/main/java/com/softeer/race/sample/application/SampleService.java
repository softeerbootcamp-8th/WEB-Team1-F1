package com.softeer.race.sample.application;

import com.softeer.race.sample.domain.Sample;
import org.springframework.stereotype.Service;

@Service
public class SampleService {

    public String greet(String name) {
        Sample sample = new Sample(name);
        return sample.greeting();
    }
}
