package org.dotwebstack.framework.service.openapi.param;

import static org.dotwebstack.framework.service.openapi.fromcore.ExceptionHelper.illegalArgumentException;
import static org.dotwebstack.framework.service.openapi.fromcore.ExceptionHelper.illegalStateException;
import static org.dotwebstack.framework.service.openapi.helper.DwsExtensionHelper.supportsDwsType;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.ARRAY_TYPE;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.STRING_TYPE;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_EXPANDED_PARAMS;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_EXPAND_TYPE;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Stream;
import lombok.NonNull;
import org.dotwebstack.framework.service.openapi.helper.DwsExtensionHelper;
import org.dotwebstack.framework.service.openapi.helper.JsonNodeUtils;
import org.dotwebstack.framework.service.openapi.response.ResponseSchemaContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

@Component
public class ExpandParamHandler extends DefaultParamHandler {

  public ExpandParamHandler(OpenAPI openApi) {
    super(openApi);
  }

  @Override
  public boolean supports(Parameter parameter) {
    return supportsDwsType(parameter, X_DWS_EXPAND_TYPE);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Optional<Object> getValue(@NonNull ServerRequest request, @NonNull Parameter parameter,
      @NonNull ResponseSchemaContext responseSchemaContext) {
    Optional<Object> expandValueOptional = super.getValue(request, parameter, responseSchemaContext);

    if (expandValueOptional.isPresent()) {
      List<String> allValues = new ArrayList<>();
      ((List<String>) expandValueOptional.get()).forEach(value -> {
        String[] path = value.split("\\.");
        var pathBuilder = new StringJoiner(".");
        Stream.of(path)
            .forEach(pathElement -> {
              pathBuilder.add(pathElement);
              if (!allValues.contains(pathBuilder.toString())) {
                allValues.add(pathBuilder.toString());
              }
            });
      });
      Collections.sort(allValues);
      return Optional.of(allValues);
    }
    return expandValueOptional;
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void validate(@NonNull String fieldName, @NonNull Parameter parameter, @NonNull String pathName) {
    var schema = parameter.getSchema();

    switch (schema.getType()) {
      case ARRAY_TYPE:
        if (Objects.nonNull(schema.getDefault())) {
          ((ArrayList<String>) Objects.requireNonNull(JsonNodeUtils.toObject((ArrayNode) schema.getDefault())))
              .forEach(defaultValue -> {
                validateExpandParam(fieldName, defaultValue, pathName);
                validateValues(defaultValue, parameter);
              });
        }
        ((ArraySchema) schema).getItems()
            .getEnum()
            .forEach((enumParam -> validateExpandParam(fieldName, (String) enumParam, pathName)));
        break;
      case STRING_TYPE:
        if (Objects.nonNull(schema.getDefault())) {
          validateExpandParam(fieldName, ((StringSchema) schema).getDefault(), pathName);
          validateValues(((StringSchema) schema).getDefault(), parameter);
        }
        ((StringSchema) schema).getEnum()
            .forEach(enumParam -> validateExpandParam(fieldName, enumParam, pathName));
        break;
      default:
        throw illegalArgumentException("Expand parameter '{}' can only be of type array or string for path '{}'",
            parameter.getName(), pathName);
    }
  }

  private Schema<?> getPropertySchema(Schema<?> schema, String fieldName) {
    if (schema instanceof ComposedSchema) {
      var composedSchema = (ComposedSchema) schema;

      return getComposedChilds(composedSchema).stream()
          .filter(ObjectSchema.class::isInstance)
          .filter(DwsExtensionHelper::isTransient)
          .map(subSchema -> getPropertySchema(subSchema, fieldName))
          .filter(Objects::nonNull)
          .findFirst()
          .orElse(null);
    }

    return schema.getProperties()
        .get(fieldName);
  }

  private Schema<?> getPropertySchema(String fieldName, String pathName) {
    // TODO
    return null;
  }

  @SuppressWarnings("rawtypes")
  private List<Schema> getComposedChilds(ComposedSchema composedSchema) {
    if (composedSchema.getAllOf() != null) {
      return composedSchema.getAllOf();
    } else if (composedSchema.getAnyOf() != null) {
      return composedSchema.getAnyOf();
    } else if (composedSchema.getOneOf() != null) {
      return composedSchema.getOneOf();
    }

    throw illegalStateException("Composed Schema is empty!");
  }

  private void validateExpandParam(String fieldName, String expandValue, String pathName) {

  }

  @Override
  public String getParameterName(Parameter param) {
    return X_DWS_EXPANDED_PARAMS;
  }
}
