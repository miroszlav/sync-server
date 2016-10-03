package com.github.tornaia.sync.server;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

import static org.springframework.boot.SpringApplication.run;

@EnableWebSocket
@SpringBootApplication
@ComponentScan("com.github.tornaia.sync.server")
public class ServerApp {

    public static void main(String[] args) throws Exception {
        run(ServerApp.class, args);
    }
}
