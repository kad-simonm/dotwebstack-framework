package org.dotwebstack.framework.service.openapi.response;

import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_EXPANDED_PARAMS;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import lombok.NonNull;
import org.dotwebstack.framework.service.openapi.exception.ExceptionHelper;
import org.dotwebstack.framework.service.openapi.helper.OasConstants;
import org.dotwebstack.framework.service.openapi.query.Field;

public class ResponseContextHelper {

  private ResponseContextHelper() {
  }

  public static Set<String> getPathsForSuccessResponse(@NonNull ResponseSchemaContext responseSchemaContext,
                                                       @NonNull Map<String, Object> inputParams) {
    Optional<ResponseTemplate> successResponse = responseSchemaContext.getResponses()
        .stream()
        .filter(template -> template.isApplicable(200, 299) || template.isApplicable(300, 303))
        .findFirst();

    if (successResponse.isPresent()) {
      var responseTemplate = successResponse.get();
      Set<String> requiredFields =
          getResponseObject(responseSchemaContext.getFieldName(), inputParams, responseTemplate);
      requiredFields.addAll(responseSchemaContext.getRequiredFields());
      return requiredFields;
    } else {
      throw ExceptionHelper.illegalArgumentException("No success response found for ResponseSchemaContext!");
    }
  }

  private static Set<String> getResponseObject(String graphQlField, Map<String, Object> inputParams,
                                               ResponseTemplate responseTemplate) {
    var responseObject = responseTemplate.getResponseObject();

    if (responseObject == null) {
      return Collections.emptySet();
    }

    return new HashSet<>(getRequiredResponseObject("", responseObject, inputParams).keySet());
  }

  static Map<String, SchemaSummary> getRequiredResponseObject(String prefix, ResponseObject responseObject,
                                                              Map<String, Object> inputParams) {
    Map<String, SchemaSummary> responseObjects = new HashMap<>();
    var joiner = getStringJoiner(prefix);
    SchemaSummary summary = responseObject.getSummary();
    boolean isExpanded = isExpanded(inputParams, getPathString(prefix, responseObject));
    addPrefixToPath(summary, responseObject, joiner, responseObjects, isExpanded);
    if (summary.isRequired() || summary.isTransient() || isExpanded) {
      handleSubSchemas(inputParams, responseObjects, joiner, responseObject);
    }
    return responseObjects;
  }

  private static void addPrefixToPath(SchemaSummary summary, ResponseObject responseObject, StringJoiner joiner,
                                      Map<String, SchemaSummary> responseObjects, boolean isExpanded) {
    /*
     * Based on the required fields from the OAS resposne a GraphQL query is constructed. Some fields
     * however do only exist in OAS and not in GraphQL. To deal with this properly, the following rules
     * are in place:
     */

    // envelope objects do not exist in graphql, no prefix should be added
    if (summary.isTransient()) {
      return;
    }

    // root objects in graphql are unnamed, so the prefix for root objects in oas should not be added
    if (responseObject.getParent() == null) {
      return;
    }

    // arrays are wrapped around an object with the same name in oas, add prefix only for object
    if (Objects.equals(OasConstants.ARRAY_TYPE, summary.getType())) {
      return;
    }

    // check to see if the object is a hidden root (it only has parents that do not exist in graphql)
    if (Objects.equals(OasConstants.OBJECT_TYPE, summary.getType()) && isHiddenRoot(responseObject)) {
      return;
    }

    joiner.add(responseObject.getIdentifier());
    if (summary.isRequired() || isExpanded) {
      responseObjects.put(joiner.toString(), summary);
    }
  }

  private static boolean isHiddenRoot(ResponseObject responseObject) {
    var parent = responseObject.getParent();
    while (parent != null) {
      if (!parent.getSummary()
          .isTransient()
          && !Objects.equals(OasConstants.ARRAY_TYPE, parent.getSummary()
          .getType())) {
        return false;
      }
      parent = parent.getParent();
    }

    return true;
  }

  private static void handleSubSchemas(Map<String, Object> inputParams,
                                       Map<String, SchemaSummary> responseObjects, StringJoiner joiner,
                                       ResponseObject responseObject) {

    var prefix = joiner.toString();
    List<ResponseObject> subSchemas;

    SchemaSummary summary = responseObject.getSummary();
    if (!summary.getChildren()
        .isEmpty()) {
      subSchemas = summary.getChildren();
    } else if (!summary.getComposedOf()
        .isEmpty()) {
      subSchemas = summary.getComposedOf();
    } else if (!summary.getItems()
        .isEmpty()) {
      subSchemas = summary.getItems();
    } else {
      return;
    }

    extractResponseObjects(prefix, subSchemas, inputParams, responseObjects);
  }

  private static void extractResponseObjects(String prefix, List<ResponseObject> children,
                                             Map<String, Object> inputParams,
                                             Map<String, SchemaSummary> responseObjects) {
    children.stream()
        .flatMap(child -> getRequiredResponseObject(prefix, child, inputParams).entrySet()
            .stream())
        .forEach(entry -> responseObjects.put(entry.getKey(), entry.getValue()));
  }

  public static String getPathString(String prefix, ResponseObject responseObject) {
    var expandJoiner = new StringJoiner(".");
    if (!prefix.isBlank()) {
      expandJoiner.add(prefix);
    }
    expandJoiner.add(responseObject.getIdentifier());
    return expandJoiner.toString();
  }

  private static StringJoiner getStringJoiner(String prefix) {
    var joiner = new StringJoiner(".");
    if (!prefix.isEmpty()) {
      joiner.add(prefix);
    }
    return joiner;
  }

  @SuppressWarnings("unchecked")
  public static boolean isExpanded(Map<String, Object> inputParams, @NonNull String path) {
    if (Objects.isNull(inputParams)) {
      return false;
    }
    List<String> expandVariables = (List<String>) inputParams.get(X_DWS_EXPANDED_PARAMS);
    if (Objects.nonNull(expandVariables)) {
      return expandVariables.stream()
          .anyMatch(path::equals);
    }
    return false;
  }
}
