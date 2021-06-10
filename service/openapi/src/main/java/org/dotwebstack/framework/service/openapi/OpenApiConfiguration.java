package org.dotwebstack.framework.service.openapi;

import static org.dotwebstack.framework.service.openapi.helper.DwsExtensionHelper.isDwsOperation;
import static org.springframework.web.reactive.function.server.RequestPredicates.OPTIONS;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import org.dotwebstack.framework.service.openapi.fromcore.ResponseMapper;
import org.dotwebstack.framework.service.openapi.graphql.GraphQlProxy;
import org.dotwebstack.framework.service.openapi.handler.CoreRequestHandler;
import org.dotwebstack.framework.service.openapi.handler.OpenApiRequestHandler;
import org.dotwebstack.framework.service.openapi.handler.OptionsRequestHandler;
import org.dotwebstack.framework.service.openapi.helper.DwsExtensionHelper;
import org.dotwebstack.framework.service.openapi.mapping.EnvironmentProperties;
import org.dotwebstack.framework.service.openapi.mapping.JsonResponseMapper;
import org.dotwebstack.framework.service.openapi.param.ParamHandlerRouter;
import org.dotwebstack.framework.service.openapi.requestbody.RequestBodyHandlerRouter;
import org.dotwebstack.framework.service.openapi.response.RequestBodyContextBuilder;
import org.dotwebstack.framework.service.openapi.response.ResponseSchemaContext;
import org.dotwebstack.framework.service.openapi.response.ResponseTemplate;
import org.dotwebstack.framework.service.openapi.response.ResponseTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class OpenApiConfiguration {

  private static final String STATIC_ASSETS_LOCATION = "assets/";

  private final OpenAPI openApi;

  private final InputStream openApiStream;

  private final List<ResponseMapper> responseMappers;

  private final JsonResponseMapper jsonResponseMapper;

  private final ParamHandlerRouter paramHandlerRouter;

  private final RequestBodyHandlerRouter requestBodyHandlerRouter;

  private final OpenApiProperties openApiProperties;

  private final EnvironmentProperties environmentProperties;

  private final GraphQlProxy graphQlProxy;

  public OpenApiConfiguration(OpenAPI openApi, List<ResponseMapper> responseMappers,
      JsonResponseMapper jsonResponseMapper, ParamHandlerRouter paramHandlerRouter, InputStream openApiStream,
      RequestBodyHandlerRouter requestBodyHandlerRouter, OpenApiProperties openApiProperties,
      EnvironmentProperties environmentProperties, GraphQlProxy graphQlProxy) {
    this.openApi = openApi;
    this.paramHandlerRouter = paramHandlerRouter;
    this.responseMappers = responseMappers;
    this.jsonResponseMapper = jsonResponseMapper;
    this.openApiStream = openApiStream;
    this.requestBodyHandlerRouter = requestBodyHandlerRouter;
    this.openApiProperties = openApiProperties;
    this.environmentProperties = environmentProperties;
    this.graphQlProxy = graphQlProxy;
  }

  public static class HttpAdviceTrait implements org.zalando.problem.spring.webflux.advice.http.HttpAdviceTrait {

  }

  @Bean
  public HttpAdviceTrait httpAdviceTrait() {
    return new HttpAdviceTrait();
  }

  @Bean
  public RouterFunction<ServerResponse> route(@NonNull OpenAPI openApi) {
    RouterFunctions.Builder routerFunctions = RouterFunctions.route();

    var responseTemplateBuilder = ResponseTemplateBuilder.builder()
        .openApi(openApi)
        .xdwsStringTypes(openApiProperties.getXdwsStringTypes())
        .build();
    var requestBodyContextBuilder = new RequestBodyContextBuilder(openApi);
    openApi.getPaths()
        .forEach((name, path) -> {
          Optional<List<HttpMethodOperation>> operations = Optional.of(path)
              .map(p -> getHttpMethodOperations(path, name));

          operations.flatMap(this::toOptionRouterFunction)
              .ifPresent(routerFunctions::add);

          operations.ifPresent(httpMethodOperations -> Stream.of(httpMethodOperations)
              .flatMap(Collection::stream)
              .map(httpMethodOperation -> toRouterFunctions(responseTemplateBuilder, requestBodyContextBuilder,
                  httpMethodOperation))
              .forEach(routerFunctions::add));

        });

    addOpenApiSpecEndpoints(routerFunctions, openApiStream);
    return routerFunctions.build();
  }

  protected void addOpenApiSpecEndpoints(RouterFunctions.Builder routerFunctions, @NonNull InputStream openApiStream) {
    RequestPredicate getPredicate = RequestPredicates.method(HttpMethod.GET)
        .and(RequestPredicates.path(openApiProperties.getApiDocPublicationPath()))
        .and(accept(MediaType.APPLICATION_JSON));

    routerFunctions.add(RouterFunctions.route(OPTIONS(openApiProperties.getApiDocPublicationPath()),
        new OptionsRequestHandler(List.of(HttpMethod.GET))));
    routerFunctions.add(RouterFunctions.route(getPredicate, new OpenApiRequestHandler(openApiStream)));
  }

  private List<HttpMethodOperation> getHttpMethodOperations(PathItem pathItem, String name) {
    HttpMethodOperation.HttpMethodOperationBuilder builder = HttpMethodOperation.builder()
        .name(name);

    List<HttpMethodOperation> httpMethodOperations = new ArrayList<>();

    if (Objects.nonNull(pathItem.getGet())) {
      httpMethodOperations.add(builder.httpMethod(HttpMethod.GET)
          .operation(pathItem.getGet())
          .build());
    }
    if (Objects.nonNull(pathItem.getPost())) {
      httpMethodOperations.add(builder.httpMethod(HttpMethod.POST)
          .operation(pathItem.getPost())
          .build());
    }

    return httpMethodOperations.stream()
        .filter(httpMethodOperation -> isDwsOperation(httpMethodOperation.getOperation()))
        .collect(Collectors.toList());
  }

  protected RouterFunction<ServerResponse> toRouterFunctions(ResponseTemplateBuilder responseTemplateBuilder,
      RequestBodyContextBuilder requestBodyContextBuilder, HttpMethodOperation httpMethodOperation) {
    var requestBodyContext = requestBodyContextBuilder.buildRequestBodyContext(httpMethodOperation.getOperation()
        .getRequestBody());

    List<ResponseTemplate> responseTemplates = responseTemplateBuilder.buildResponseTemplates(httpMethodOperation);

    List<String> requiredFields = DwsExtensionHelper.getDwsRequiredFields(httpMethodOperation.getOperation());

    var responseSchemaContext = ResponseSchemaContext.builder()
        .fieldName("TODO")
        .requiredFields(Objects.nonNull(requiredFields) ? requiredFields : Collections.emptyList())
        .responses(responseTemplates)
        .parameters(httpMethodOperation.getOperation()
            .getParameters() != null ? httpMethodOperation.getOperation()
                .getParameters() : Collections.emptyList())
        .dwsParameters(DwsExtensionHelper.getDwsQueryParameters(httpMethodOperation.getOperation()))
        .requestBodyContext(requestBodyContext)
        .build();

    var requestPredicate = RequestPredicates.method(httpMethodOperation.getHttpMethod())
        .and(RequestPredicates.path(httpMethodOperation.getName()));

    var coreRequestHandler =
        new CoreRequestHandler(openApi, graphQlProxy, httpMethodOperation.getName(), responseSchemaContext,
            responseMappers, jsonResponseMapper, paramHandlerRouter, requestBodyHandlerRouter, environmentProperties);

    return RouterFunctions.route(requestPredicate, coreRequestHandler);
  }


  protected Optional<RouterFunction<ServerResponse>> toOptionRouterFunction(
      List<HttpMethodOperation> httpMethodOperations) {
    if (httpMethodOperations == null || httpMethodOperations.isEmpty()) {
      return Optional.empty();
    }

    List<HttpMethod> httpMethods = httpMethodOperations.stream()
        .map(HttpMethodOperation::getHttpMethod)
        .collect(Collectors.toList());

    return Optional.of(RouterFunctions.route(OPTIONS(httpMethodOperations.get(0)
        .getName()), new OptionsRequestHandler(httpMethods)));
  }
}
