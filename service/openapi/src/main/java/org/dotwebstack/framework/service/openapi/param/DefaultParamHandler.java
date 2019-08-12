package org.dotwebstack.framework.service.openapi.param;

import static io.swagger.v3.oas.models.parameters.Parameter.StyleEnum.FORM;
import static io.swagger.v3.oas.models.parameters.Parameter.StyleEnum.PIPEDELIMITED;
import static io.swagger.v3.oas.models.parameters.Parameter.StyleEnum.SIMPLE;
import static io.swagger.v3.oas.models.parameters.Parameter.StyleEnum.SPACEDELIMITED;
import static org.dotwebstack.framework.service.openapi.exception.OpenApiExceptionHelper.parameterValidationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.dotwebstack.framework.core.helpers.ExceptionHelper;
import org.dotwebstack.framework.core.query.GraphQlField;
import org.dotwebstack.framework.service.openapi.exception.ParameterValidationException;
import org.dotwebstack.framework.service.openapi.helper.JsonNodeUtils;
import org.dotwebstack.framework.service.openapi.helper.SchemaUtils;
import org.springframework.web.reactive.function.server.ServerRequest;

public class DefaultParamHandler implements ParamHandler {

  private static final String ARRAY_TYPE = "array";

  private static final String OBJECT_TYPE = "object";

  private static final String STRING_TYPE = "string";

  private static final String PARAM_PATH_TYPE = "path";

  private static final String PARAM_QUERY_TYPE = "query";

  private static final String PARAM_HEADER_TYPE = "header";

  private final OpenAPI openApi;

  public DefaultParamHandler(OpenAPI openApi) {
    this.openApi = openApi;
  }

  @Override
  public boolean supports(Parameter parameter) {
    return true;
  }

  @Override
  public Optional<Object> getValue(ServerRequest request, Parameter parameter) throws ParameterValidationException {
    Object paramValue;
    switch (parameter.getIn()) {
      case PARAM_PATH_TYPE:
        paramValue = getPathParam(parameter, request);
        break;
      case PARAM_QUERY_TYPE:
        paramValue = getQueryParam(parameter, request);
        break;
      case PARAM_HEADER_TYPE:
        paramValue = getHeaderParam(parameter, request);
        break;
      default:
        throw ExceptionHelper.illegalArgumentException("Unsupported value for parameters.in: '{}'.", parameter.getIn());
    }

    if (Objects.nonNull(paramValue)) {
      Object convertedValue = deserialize(parameter, paramValue);
      validateEnumValues(convertedValue, parameter);
      return Optional.of(convertedValue);
    } else {
      Optional<Object> defaultValue = getDefault(parameter);
      if (defaultValue.isPresent()) {
        validateEnumValues(defaultValue.get(), parameter);
      } else {
        if (parameter.getRequired()) {
          throw parameterValidationException("No value provided for required query parameter '{}'.",
              parameter.getName());
        }
      }

      return defaultValue;
    }
  }

  @Override
  public void validate(GraphQlField field, Parameter parameter, String pathName) {
    String name = parameter.getName();
    long matching = field.getArguments()
        .stream()
        .filter(argument -> argument.getName()
            .equals(name))
        .count();
    if (matching == 0) {
      throw ExceptionHelper.invalidConfigurationException(
          "OAS argument '{}' for path '{}' was not found on GraphQL field '{}'", name, pathName, field.getName());
    }
  }

  @SuppressWarnings("unchecked")
  private void validateEnumValues(Object paramValue, Parameter parameter) throws ParameterValidationException {
    String type = parameter.getSchema()
        .getType();
    switch (type) {
      case ARRAY_TYPE:
        validateEnumValuesForArray(paramValue, parameter);
        break;
      case STRING_TYPE:
        if (Objects.nonNull(parameter.getSchema()
            .getEnum())
            && !parameter.getSchema()
                .getEnum()
                .contains(paramValue)) {
          throw parameterValidationException("Parameter '{}' has an invalid value, should be one of: '%s'",
              parameter.getName(), String.join(",", parameter.getSchema()
                  .getEnum()));
        }
        break;
      default:
        if (Objects.nonNull(parameter.getSchema()
            .getEnum())) {
          throw parameterValidationException("Sort parameter '{}' is of wrong type, can only be string of string[]",
              parameter.getName());
        }
    }
  }

  @SuppressWarnings("unchecked")
  private void validateEnumValuesForArray(Object paramValue, Parameter parameter) throws ParameterValidationException {
    if (Objects.nonNull(((ArraySchema) parameter.getSchema()).getItems()
        .getEnum())) {
      List<String> list;
      List<String> enumList = (List<String>) ((ArraySchema) parameter.getSchema()).getItems()
          .getEnum();
      if (paramValue instanceof String) {
        list = Stream.of(((String) paramValue).replace("[", "")
            .replace("]", ""))
            .collect(Collectors.toList());
      } else if (paramValue instanceof List) {
        list = (List<String>) paramValue;
      } else {
        throw parameterValidationException("Enumerated parameter '%s' can only be of string or string[]",
            parameter.getName());
      }
      List<String> invalidValues = list.stream()
          .filter(param -> !enumList.contains(param))
          .collect(Collectors.toList());

      if (!invalidValues.isEmpty()) {
        throw parameterValidationException("Parameter '{}' has (an) invalid value(s): '{}', should be one of: '{}'",
            parameter.getName(), String.join(", ", invalidValues), String.join(", ", enumList));
      }
    }
  }

  private Object deserialize(Parameter parameter, Object paramValue) {
    if (paramValue == null) {
      return null;
    }
    String schemaType = parameter.getSchema()
        .get$ref() != null ? OBJECT_TYPE
            : parameter.getSchema()
                .getType();
    switch (schemaType) {
      case ARRAY_TYPE:
        return deserializeArray(parameter, paramValue);
      case OBJECT_TYPE:
        return deserializeObject(parameter, paramValue);
      default:
        return paramValue;
    }
  }

  private Object deserializeArray(Parameter parameter, Object paramValue) {
    Parameter.StyleEnum style = parameter.getStyle();
    boolean explode = parameter.getExplode();

    if (style == SIMPLE && !explode) {
      return ImmutableList.copyOf(((String) paramValue).split(","));
    } else if (style == FORM && !explode) {
      return ImmutableList.copyOf(((String) paramValue).split(","));
    } else if (style == SPACEDELIMITED && !explode) {
      return ImmutableList.copyOf(((String) paramValue).split(" "));
    } else if (style == PIPEDELIMITED && !explode) {
      return ImmutableList.copyOf(((String) paramValue).split("\\|"));
    } else {
      throw ExceptionHelper.unsupportedOperationException(
          "Array deserialization not supported for parameter with 'explode=false' and style "
              + "'{}'. Supported styles are '{}'.",
          style, ImmutableList.of(SIMPLE, FORM, SPACEDELIMITED, PIPEDELIMITED));
    }
  }

  private Object deserializeObject(Parameter parameter, Object paramValue) {
    Parameter.StyleEnum style = parameter.getStyle();
    boolean explode = parameter.getExplode();

    if (style == SIMPLE) {
      if (explode) {
        return deserializeObjectFromKeyValueString((String) paramValue, ",", "=");
      }
      return deserializeObjectFromKeyValueString((String) paramValue);
    } else {
      throw ExceptionHelper.unsupportedOperationException(
          "Object deserialization not supported for parameter style " + "'{}'. Supported styles are '{}'.", style,
          ImmutableList.of(SIMPLE));
    }
  }

  private Object deserializeObjectFromKeyValueString(String keyValueString) {
    String[] split = keyValueString.split(",");
    if (split.length % 2 != 0) {
      throw ExceptionHelper.illegalArgumentException("Key value string '{}' should contain an even number of elements.",
          keyValueString);
    }
    Map<String, String> result = new HashMap<>();
    for (int i = 0; i < split.length; i += 2) {
      String key = split[i];
      String value = split[i + 1];
      result.put(key, value);
    }
    return result;
  }

  private Object deserializeObjectFromKeyValueString(String keyValueString, String elementSeparator,
      String keyValueSeparator) {
    Map<String, String> result = new HashMap<>();
    Arrays.asList(keyValueString.split(elementSeparator))
        .forEach(keyValue -> {
          String[] split = keyValue.split(keyValueSeparator);
          if (split.length != 2) {
            throw ExceptionHelper.illegalArgumentException(
                "Key value element '{}' with separator '{}' should have one " + "key and one value.", keyValue,
                keyValueSeparator);
          }
          result.put(split[0], split[1]);
        });
    return result;
  }

  private Object getPathParam(Parameter parameter, ServerRequest request) {
    try {
      return request.pathVariable(parameter.getName());
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private Object getQueryParam(Parameter parameter, ServerRequest request) {
    List<String> result = request.queryParams()
        .get(parameter.getName());

    if (ARRAY_TYPE.equals(parameter.getSchema()
        .getType()) && parameter.getExplode()) {
      return result;
    }
    return (!Objects.isNull(result) && !result.isEmpty()) ? result.get(0) : null;
  }

  private Object getHeaderParam(Parameter parameter, ServerRequest request) {
    List<String> result = request.headers()
        .header(parameter.getName());
    return !result.isEmpty() ? result.get(0) : null;
  }

  @SuppressWarnings("rawtypes")
  Optional<Object> getDefault(Parameter parameter) {
    Schema schema = parameter.getSchema()
        .get$ref() != null ? SchemaUtils.getSchemaReference(
            parameter.getSchema()
                .get$ref(),
            openApi) : parameter.getSchema();
    if (schema != null && schema.getDefault() != null) {
      switch (schema.getType()) {
        case ARRAY_TYPE:
        case OBJECT_TYPE:
          return Optional.ofNullable(JsonNodeUtils.toObject((JsonNode) schema.getDefault()));
        default:
          return Optional.of(schema.getDefault());
      }
    }
    return Optional.empty();
  }
}