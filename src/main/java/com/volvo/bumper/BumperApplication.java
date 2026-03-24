package com.volvo.bumper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.volvo.bumper.config")
public class BumperApplication {

  public static void main(String[] args) {
    if (args.length == 0) {
      args = new String[] {"help"};
    }
    SpringApplication.run(BumperApplication.class, args);
  }
}
