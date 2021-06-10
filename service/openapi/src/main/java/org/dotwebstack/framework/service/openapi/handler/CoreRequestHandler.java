package org.dotwebstack.framework.service.openapi.handler;

import static java.util.Collections.emptyList;
import static org.dotwebstack.framework.service.openapi.exception.OpenApiExceptionHelper.graphQlErrorException;
import static org.dotwebstack.framework.service.openapi.exception.OpenApiExceptionHelper.mappingException;
import static org.dotwebstack.framework.service.openapi.exception.OpenApiExceptionHelper.noContentException;
import static org.dotwebstack.framework.service.openapi.exception.OpenApiExceptionHelper.notAcceptableException;
import static org.dotwebstack.framework.service.openapi.helper.GraphQlFormatHelper.formatQuery;
import static org.dotwebstack.framework.service.openapi.helper.RequestBodyResolver.resolveRequestBody;
import static org.dotwebstack.framework.service.openapi.response.ResponseWriteContextHelper.createNewDataStack;
import static org.dotwebstack.framework.service.openapi.response.ResponseWriteContextHelper.createNewResponseWriteContext;
import static org.springframework.web.reactive.function.BodyInserters.fromPublisher;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.InputMapDefinesTooManyFieldsException;
import graphql.execution.NonNullableValueCoercedAsNullException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.dotwebstack.framework.service.openapi.exception.BadRequestException;
import org.dotwebstack.framework.service.openapi.exception.GraphQlErrorException;
import org.dotwebstack.framework.service.openapi.fromcore.ResponseMapper;
import org.dotwebstack.framework.service.openapi.graphql.GraphQlProxy;
import org.dotwebstack.framework.service.openapi.helper.SchemaResolver;
import org.dotwebstack.framework.service.openapi.mapping.EnvironmentProperties;
import org.dotwebstack.framework.service.openapi.mapping.JsonResponseMapper;
import org.dotwebstack.framework.service.openapi.param.ParamHandlerRouter;
import org.dotwebstack.framework.service.openapi.query.GraphQlQueryBuilder;
import org.dotwebstack.framework.service.openapi.requestbody.RequestBodyHandlerRouter;
import org.dotwebstack.framework.service.openapi.response.RequestBodyContext;
import org.dotwebstack.framework.service.openapi.response.ResponseSchemaContext;
import org.dotwebstack.framework.service.openapi.response.ResponseTemplate;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.zalando.problem.ThrowableProblem;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class CoreRequestHandler implements HandlerFunction<ServerResponse> {

  private static final String DEFAULT_ACCEPT_HEADER_VALUE = "*/*";

  private static final String MDC_REQUEST_ID = "requestId";

  private static final String REQUEST_URI = "request_uri";

  private final OpenAPI openApi;

  private final GraphQlProxy graphQlProxy;

  private final ResponseSchemaContext responseSchemaContext;

  private final List<ResponseMapper> responseMappers;

  private final JsonResponseMapper jsonResponseMapper;

  private final ParamHandlerRouter paramHandlerRouter;

  private final RequestBodyHandlerRouter requestBodyHandlerRouter;

  private final String pathName;

  private final EnvironmentProperties properties;

  public CoreRequestHandler(OpenAPI openApi, GraphQlProxy graphQlProxy, String pathName,
      ResponseSchemaContext responseSchemaContext, List<ResponseMapper> responseMappers,
      JsonResponseMapper jsonResponseMapper, ParamHandlerRouter paramHandlerRouter,
      RequestBodyHandlerRouter requestBodyHandlerRouter, EnvironmentProperties properties) {
    this.openApi = openApi;
    this.graphQlProxy = graphQlProxy;
    this.pathName = pathName;
    this.responseSchemaContext = responseSchemaContext;
    this.responseMappers = responseMappers;
    this.jsonResponseMapper = jsonResponseMapper;
    this.paramHandlerRouter = paramHandlerRouter;
    this.requestBodyHandlerRouter = requestBodyHandlerRouter;
    this.properties = properties;
  }

  @Override
  public Mono<ServerResponse> handle(ServerRequest request) {
    var requestId = UUID.randomUUID()
        .toString();
    return Mono.fromCallable(() -> getResponse(request, requestId))
        .publishOn(Schedulers.boundedElastic());
  }

  @SuppressWarnings("rawtypes")
  Map<String, Schema> getRequestBodyProperties(RequestBodyContext requestBodyContext) {
    if (Objects.nonNull(requestBodyContext) && Objects.nonNull(requestBodyContext.getRequestBodySchema())) {
      var mediaType = requestBodyContext.getRequestBodySchema()
          .getContent()
          .get(MediaType.APPLICATION_JSON.toString());
      Schema<?> schema = SchemaResolver.resolveSchema(openApi, mediaType.getSchema(), mediaType.getSchema()
          .get$ref());
      return schema.getProperties();
    } else {
      return Collections.emptyMap();
    }
  }

  @SuppressWarnings({"rawtypes"})
  ServerResponse getResponse(ServerRequest request, String requestId)
      throws GraphQlErrorException, BadRequestException {
    MDC.put(MDC_REQUEST_ID, requestId);
    Map<String, Object> inputParams = resolveParameters(request);

    ExecutionResult result = buildQueryString(inputParams).map(query -> {
      if (LOG.isDebugEnabled()) {
        logInputRequest(request);
        LOG.debug("GraphQL query is:\n\n{}\n", formatQuery(query));
      }

      var executionInput = ExecutionInput.newExecutionInput()
          .query(query)
          .variables(inputParams)
          .build();


      return graphQlProxy.execute(executionInput);
    })
        .orElse(new ExecutionResultImpl(new HashMap<String, Object>(), emptyList()));

    if (result.getErrors()
        .isEmpty()) {

      var httpStatus = getHttpStatus();
      if (httpStatus.is3xxRedirection()) {

        return ServerResponse.status(httpStatus)
            .build()
            .block();
      }

      Object queryResultData = ((Map) result.getData()).values()
          .iterator()
          .next();

      List<MediaType> acceptHeaders = request.headers()
          .accept();
      var template = getResponseTemplate(acceptHeaders);

      String body;

      body = getResponseMapperBody(request, inputParams, queryResultData, template);

      if (Objects.isNull(body)) {
        throw noContentException("No content found.");
      }

      var bodyBuilder = ServerResponse.ok()
          .contentType(template.getMediaType());

      return bodyBuilder.body(fromPublisher(Mono.just(body), String.class))
          .block();
    }

    throw unwrapExceptionWhileNeeded(result);
  }

  private GraphQlErrorException unwrapExceptionWhileNeeded(ExecutionResult result) throws GraphQlErrorException {
    var graphQlError = result.getErrors()
        .get(0);

    Optional<ThrowableProblem> throwableProblem = Optional.of(graphQlError)
        .filter(ThrowableProblem.class::isInstance)
        .map(ThrowableProblem.class::cast);

    if (throwableProblem.isPresent()) {
      throw throwableProblem.get();
    }

    if (graphQlError instanceof InputMapDefinesTooManyFieldsException) {
      throw graphQlErrorException("Too many request fields", graphQlError);
    }

    if (graphQlError instanceof NonNullableValueCoercedAsNullException) {
      throw graphQlErrorException("Missing request fields", graphQlError);
    }

    return graphQlErrorException("GraphQL query returned errors: {}", result.getErrors());
  }

  private String getResponseMapperBody(ServerRequest request, Map<String, Object> inputParams, Object data,
      ResponseTemplate template) {
    var uri = request.uri();

    if (Objects.nonNull(template.getResponseObject())) {
      var responseWriteContext =
          createNewResponseWriteContext(responseSchemaContext.getFieldName(), template.getResponseObject(), data,
              inputParams, createNewDataStack(new ArrayDeque<>(), data, inputParams), uri);

      return jsonResponseMapper.toResponse(responseWriteContext);

    } else {
      return getResponseMapper(template.getMediaType(), data.getClass()).toResponse(data);
    }
  }

  private HttpStatus getHttpStatus() {
    return responseSchemaContext.getResponses()
        .stream()
        .map(ResponseTemplate::getResponseCode)
        .map(HttpStatus::valueOf)
        .filter(httpStatus1 -> httpStatus1.is2xxSuccessful() || httpStatus1.is3xxRedirection())
        .findFirst()
        .orElseThrow(() -> new RuntimeException("No response within range 2xx 3xx configured."));
  }

  private boolean isQueryExecuted(Map<String, Object> resultData) {
    return !resultData.isEmpty();
  }

  private ResponseMapper getResponseMapper(MediaType mediaType, Class<?> dataObjectType) {
    return responseMappers.stream()
        .filter(rm -> rm.supportsOutputMimeType(mediaType))
        .filter(rm -> rm.supportsInputObjectClass(dataObjectType))
        .reduce((element, otherElement) -> {
          throw mappingException(
              "Duplicate response mapper found for input data object type '{}' and output media type '{}'.",
              dataObjectType, mediaType);
        })
        .orElseThrow(() -> mappingException(
            "No response mapper found for input data object type '{}' and output media type '{}'.", dataObjectType,
            mediaType));
  }

  ResponseTemplate getResponseTemplate(List<MediaType> acceptHeaders) {
    List<ResponseTemplate> responseTemplates = responseSchemaContext.getResponses();

    List<MediaType> supportedMediaTypes = responseTemplates.stream()
        .filter(response -> response.isApplicable(200, 299))
        .map(ResponseTemplate::getMediaType)
        .collect(Collectors.toList());

    MediaType responseContentType =
        isAcceptHeaderProvided(acceptHeaders) ? getResponseContentType(acceptHeaders, supportedMediaTypes)
            : getDefaultResponseType(responseTemplates, supportedMediaTypes);

    return responseTemplates.stream()
        .filter(response -> response.isApplicable(200, 299))
        .filter(response -> response.getMediaType()
            .equals(responseContentType))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("No response found within the 200 range."));
  }

  Map<String, Object> resolveUrlAndHeaderParameters(ServerRequest request) {
    Map<String, Object> result = new HashMap<>();
    if (Objects.nonNull(this.responseSchemaContext.getParameters())) {
      result.put(REQUEST_URI, request.uri()
          .toString());

      for (Parameter parameter : this.responseSchemaContext.getParameters()) {
        var handler = paramHandlerRouter.getParamHandler(parameter);
        handler.getValue(request, parameter, responseSchemaContext)
            .ifPresent(value -> result.put(handler.getParameterName(parameter), value));
      }
    }
    return result;
  }

  private void logInputRequest(ServerRequest request) {
    LOG.debug("Request received at: {}", request);

    Map<Object, Object> paramMap = new LinkedHashMap<>();
    paramMap.putAll(request.queryParams());
    paramMap.putAll(request.headers()
        .asHttpHeaders());
    paramMap.putAll(request.pathVariables());
    LOG.debug("Request contains following parameters: {}", paramMap.entrySet()
        .stream()
        .map(entry -> entry.getKey() + " -> " + entry.getValue())
        .collect(Collectors.toList()));

    Mono<String> mono = request.bodyToMono(String.class);
    mono.doOnSuccess(value -> {
      if (Objects.nonNull(value)) {
        LOG.debug("Request contains the following body: {}", value);
      }
    });
  }

  Map<String, Object> resolveParameters(ServerRequest request) throws BadRequestException {
    Map<String, Object> result = resolveUrlAndHeaderParameters(request);
    var requestBodyContext = this.responseSchemaContext.getRequestBodyContext();

    if (Objects.nonNull(requestBodyContext)) {
      var requestBody = resolveRequestBody(openApi, requestBodyContext.getRequestBodySchema());

      this.requestBodyHandlerRouter.getRequestBodyHandler(requestBody)
          .getValues(request, requestBodyContext, requestBody, result)
          .forEach(result::put);
    }
    return result;
  }

  private Optional<String> buildQueryString(Map<String, Object> inputParams) {
    return new GraphQlQueryBuilder().toQuery(this.responseSchemaContext, inputParams);
  }

  private MediaType getDefaultResponseType(List<ResponseTemplate> responseTemplates,
      List<MediaType> supportedMediaTypes) {
    return responseTemplates.stream()
        .filter(ResponseTemplate::isDefault)
        .findFirst()
        .map(ResponseTemplate::getMediaType)
        .orElse(supportedMediaTypes.get(0));
  }

  private MediaType getResponseContentType(List<MediaType> requestedMediaTypes, List<MediaType> supportedMediaTypes) {
    MediaType.sortByQualityValue(requestedMediaTypes);

    for (MediaType requestedMediaType : requestedMediaTypes) {
      for (MediaType supportedMediaType : supportedMediaTypes) {
        if (requestedMediaType.isCompatibleWith(supportedMediaType)) {
          return supportedMediaType;
        }
      }
    }

    throw notAcceptableException("Unsupported media type provided");
  }

  private boolean isAcceptHeaderProvided(List<MediaType> acceptHeaders) {
    if (!acceptHeaders.isEmpty()) {
      return !(acceptHeaders.size() == 1 && acceptHeaders.get(0)
          .toString()
          .equals(DEFAULT_ACCEPT_HEADER_VALUE));
    }
    return false;
  }

}
