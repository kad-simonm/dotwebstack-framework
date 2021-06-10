package org.dotwebstack.framework.service.openapi.graphql;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProxyConfig {

  @Bean
  public HttpClient getHttpClient() {
    ProxySelector proxy;
    return HttpClient.newBuilder()
        // .proxy(ProxySelector.of(new InetSocketAddress("www-proxy.cs.kadaster.nl",8082)))
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }
}
