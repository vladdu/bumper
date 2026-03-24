package com.volvo.bumper.adapter.github;

import java.net.http.HttpClient;
import java.time.Duration;

/** Factory for {@link HttpClient} instances. */
final class HttpClients {

  private HttpClients() {}

  /** Returns an {@link HttpClient} using the JVM default SSL context and trust store. */
  static HttpClient newClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
  }
}
