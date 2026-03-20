package com.example.bumper;

import com.example.bumper.commands.HelloCommands;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.shell.core.command.annotation.EnableCommand;

@SpringBootApplication
@EnableCommand(HelloCommands.class)
public class BumperApplication {

    public static void main(String[] args) {
        SpringApplication.run(BumperApplication.class, args);
    }
}
