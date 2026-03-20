package com.example.bumper.commands;

import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class HelloCommands {

    @Command(name = "hello", description = "Greet someone")
    public String hello(
            @Option(shortName = 'n', longName = "name", defaultValue = "World") String name) {
        return "Hello, " + name + "!";
    }
}
