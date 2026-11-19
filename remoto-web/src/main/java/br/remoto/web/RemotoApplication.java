package br.remoto.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RemotoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RemotoApplication.class, args);
    }
}
