package com.sprint.acdsimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 📚 LESSON — @SpringBootApplication:
 *
 * This single annotation combines three annotations:
 *   @SpringBootConfiguration  — marks this as the root configuration class
 *   @EnableAutoConfiguration  — tells Spring Boot to auto-configure beans based on
 *                               what's on the classpath (e.g. adds Tomcat because
 *                               spring-boot-starter-web is in pom.xml)
 *   @ComponentScan            — scans this package and all sub-packages for
 *                               @Component, @Service, @Repository, @Controller beans
 *
 * @EnableAsync and @EnableScheduling live in AsyncConfig.java — they don't need to be here.
 * Splitting config into dedicated @Configuration classes keeps the main class clean.
 */
@SpringBootApplication
public class AcdsimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcdsimulatorApplication.class, args);
    }

}


