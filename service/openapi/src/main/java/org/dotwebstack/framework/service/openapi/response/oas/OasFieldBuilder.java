package org.dotwebstack.framework.service.openapi.response.oas;

import static org.dotwebstack.framework.core.helpers.ExceptionHelper.invalidConfigurationException;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_DEFAULT;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_ENVELOPE;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_EXPR;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_EXPR_FALLBACK_VALUE;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_EXPR_VALUE;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_INCLUDE;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_TRANSIENT;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_TYPE;
import static org.dotwebstack.framework.service.openapi.helper.SchemaResolver.resolveSchema;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class OasFieldBuilder {

  private final OpenAPI openApi;

  private final Map<String, OasField> resolvedMap;

  public OasFieldBuilder(OpenAPI openApi) {
    this.openApi = openApi;
    this.resolvedMap = new HashMap<>();
  }

  public OasField build(Schema<?> responseSchema) {
    return toField(responseSchema, true);
  }

  private OasField toField(Schema<?> schema, boolean required) {
    Schema<?> resolved = resolveSchema(openApi, schema);
    String schemaKey = getSchemaKey(resolved, required);
    if (resolvedMap.containsKey(schemaKey)) {
      return resolvedMap.get(schemaKey);
    }
    OasField result = null;
    if (isOneOf(resolved)) {
      result = toOneOfField(resolved, required);
    } else if (isObject(resolved)) {
      result = toObjectField(resolved, required);
    } else if (isArray(resolved)) {
      result = toArray(resolved, required);
    } else if (isScalar(resolved)) {
      result = toScalarField(resolved, required);
    }
    if (result != null) {
      resolvedMap.put(schemaKey, result);
      Boolean extension = getExtension(resolved, X_DWS_TRANSIENT, Boolean.class);
      if (extension != null) {
        result.setDwsTransient(extension);
      }
      result.setDefaultValue(getExtension(resolved, X_DWS_DEFAULT));
      result.setDwsType(getExtension(resolved, X_DWS_TYPE, String.class));
    }
    return result;
  }

  private String getSchemaKey(Schema<?> resolved, boolean required) {
    return resolved.hashCode() + "_" + required;
  }

  private OasOneOfField toOneOfField(Schema<?> schema, boolean required) {
    boolean nillable = isNillable(schema);
    List<OasField> content = ((ComposedSchema) schema).getOneOf()
        .stream()
        .map(s -> resolveSchema(openApi, s))
        .map(s -> toField(s, required))
        .collect(Collectors.toList());
    return new OasOneOfField(nillable, required, content);
  }

  private OasField toScalarField(Schema<?> schema, boolean required) {
    String expression = getExpression(schema);
    String fallbackValue = getFallback(schema);
    boolean nillable = isNillable(schema);
    if (expression == null) {
      return new OasScalarField(nillable, required, schema.getType());
    } else {
      // scalar fields with an expression are always required, set required to true
      return new OasScalarExpressionField(nillable, true, schema.getType(), expression, fallbackValue);
    }
  }

  @SuppressWarnings("unchecked")
  private String getExpression(Schema<?> schema) {
    Object value = getExtension(schema, X_DWS_EXPR);
    if (value == null) {
      return null;
    } else if (value instanceof String) {
      return (String) value;
    }
    return ((Map<String, String>) value).get(X_DWS_EXPR_VALUE);
  }

  @SuppressWarnings("unchecked")
  private String getFallback(Schema<?> schema) {
    Object value = getExtension(schema, X_DWS_EXPR);
    if (value == null || value instanceof String) {
      return null;
    }
    return ((Map<String, String>) value).get(X_DWS_EXPR_FALLBACK_VALUE);
  }


  private OasField toArray(Schema<?> schema, boolean required) {
    if (schema instanceof ArraySchema) {
      Schema<?> itemSchema = ((ArraySchema) schema).getItems();
      OasField content = toField(itemSchema, required);
      return new OasArrayField(false, required, content);
    } else {
      return new OasArrayField(false, required, null);
    }
  }

  private OasObjectField toObjectField(Schema<?> schema, boolean required) {
    boolean nillable = isNillable(schema);
    boolean isEnvelope = isEnvelopObject(schema);
    String includeExpression = getExtension(schema, X_DWS_INCLUDE, String.class);
    OasObjectField objectField = new OasObjectField(nillable, required, null, isEnvelope, includeExpression);
    if (isAllOf(schema)) {
      List<Schema<?>> schemas = ((ComposedSchema) schema).getAllOf()
          .stream()
          .map(s -> resolveSchema(openApi, s))
          .collect(Collectors.toList());
      Map<String, OasField> fields = new TreeMap<>();
      schemas.stream()
          .map(s -> toObjectField(s, required).getFields())
          .forEach(fields::putAll);
      objectField.setFields(fields);
    } else {
      resolvedMap.put(getSchemaKey(schema, required), objectField);
      Map<String, OasField> fields = new TreeMap<>();
      if (schema.getProperties() == null) {
        throw invalidConfigurationException("object schema does not have any properties", schema);
      }
      schema.getProperties()
          .forEach((key, value) -> {
            boolean propertyRequired = schema.getRequired() != null && schema.getRequired()
                .contains(key);
            OasField field = toField(value, propertyRequired);
            if (field != null) {
              fields.put(key, field);
            }
          });
      objectField.setFields(fields);
    }
    return objectField;
  }

  private Object getExtension(Schema<?> schema, String key) {
    if (schema.getExtensions() != null) {
      return schema.getExtensions()
          .get(key);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private <T> T getExtension(Schema<?> schema, String key, Class<T> clazz) {
    if (schema.getExtensions() != null) {
      Object value = schema.getExtensions()
          .get(key);
      if (value == null) {
        return null;
      } else if (value.getClass()
          .isAssignableFrom(clazz)) {
        return (T) value;
      } else {
        throw invalidConfigurationException("Cannot cast class {} to {}.", value.getClass(), clazz);
      }
    }
    return null;
  }

  private boolean isNillable(Schema<?> schema) {
    if (schema.getNullable() != null) {
      return schema.getNullable();
    }
    return false;
  }

  private boolean isArray(Schema<?> schema) {
    return schema.getType()
        .equals("array");
  }

  private boolean isObject(Schema<?> schema) {
    return schema.getType()
        .equals("object");
  }

  private boolean isEnvelopObject(Schema<?> schema) {
    return isObject(schema) && schema.getExtensions() != null && schema.getExtensions()
        .containsKey(X_DWS_ENVELOPE);
  }

  private boolean isScalar(Schema<?> schema) {
    return getExtension(schema, X_DWS_TYPE, String.class) != null || Set.of("string", "number", "boolean", "integer")
        .contains(schema.getType());
  }

  private boolean isAllOf(Schema<?> schema) {
    return schema instanceof ComposedSchema && ((ComposedSchema) schema).getAllOf() != null;
  }

  private boolean isOneOf(Schema<?> schema) {
    return schema instanceof ComposedSchema && ((ComposedSchema) schema).getOneOf() != null;
  }
}