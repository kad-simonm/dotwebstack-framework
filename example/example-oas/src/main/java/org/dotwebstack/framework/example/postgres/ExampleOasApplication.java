package org.dotwebstack.framework.example.postgres;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("org.dotwebstack.framework")
public class ExampleOasApplication {

  public static void main(String[] args) {
    SpringApplication.run(ExampleOasApplication.class, args);
  }
}
