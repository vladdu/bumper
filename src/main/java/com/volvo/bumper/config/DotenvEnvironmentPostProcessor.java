package com.volvo.bumper.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Loads {@code .env} file entries into the Spring environment at lowest priority, so real
 * environment variables always take precedence.
 *
 * <p>Uses {@link SystemEnvironmentPropertySource} so Spring's relaxed binding maps uppercase
 * underscore keys (e.g. {@code GERRIT_SSH_USER}) to dotted property names (e.g. {@code
 * gerrit.ssh.user}).
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    Map<String, Object> props = new HashMap<>();
    for (DotenvEntry entry : dotenv.entries()) {
      // Only add entries not already present as system environment variables
      if (System.getenv(entry.getKey()) == null) {
        props.put(entry.getKey(), entry.getValue());
      }
    }
    if (!props.isEmpty()) {
      environment
          .getPropertySources()
          .addLast(new SystemEnvironmentPropertySource("dotenv", props));
    }
  }
}
