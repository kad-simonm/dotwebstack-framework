package org.dotwebstack.framework.service.openapi.graphql;

import static java.util.Collections.emptyList;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import org.springframework.stereotype.Service;

@Service
public class GraphQlProxy {

  private final HttpClient client;

  public GraphQlProxy(HttpClient client) {
    this.client = client;
  }

  public ExecutionResult execute(ExecutionInput executionInput) {
    URI uri = URI.create("https://a83a9327-c1b8-42f8-b3c5-049baf943a84.mock.pstmn.io/graphql");
    HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(executionInput.getQuery());
    HttpRequest request = HttpRequest.newBuilder()
        .uri(uri)
        .header("content-type", "application/graphql")
        .POST(bodyPublisher)
        .build();
    HttpResponse.BodyHandler<String> responseBodyHandler = HttpResponse.BodyHandlers.ofString();
    try {
      HttpResponse<String> response = client.send(request, responseBodyHandler);
      String body = response.body();
      return new ExecutionResultImpl(new ObjectMapper().readValue(body, HashMap.class)
          .get("data"), emptyList());
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}
