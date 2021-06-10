package org.dotwebstack.framework.service.openapi.query;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.parameters.Parameter;
import lombok.NonNull;
import org.dotwebstack.framework.service.openapi.response.ResponseObject;
import org.dotwebstack.framework.service.openapi.response.ResponseSchemaContext;
import org.dotwebstack.framework.service.openapi.response.ResponseTemplate;

public class GraphQlQueryBuilder {

  public Optional<String> toQuery(@NonNull ResponseSchemaContext responseSchemaContext,
                                  @NonNull Map<String, Object> inputParams) {

    // Set<String> requiredPaths = getPathsForSuccessResponse(responseSchemaContext, inputParams);
    ResponseTemplate okResponse = responseSchemaContext.getOkResponse();

    //TODO: add expand
    SimpleQuery.SimpleQueryBuilder builder = SimpleQuery.builder();
    Field root = toField(okResponse);
    builder.name("wrapper");
    builder.field(root);

    addFilters(root, responseSchemaContext.getParameters(), inputParams);

    SimpleQuery simpleQuery = builder.build();
    return Optional.of(simpleQuery.toString());

  }

  private void addFilters(Field root, List<Parameter> parameters, Map<String, Object> inputParams) {
    List<Parameter> filterParams =
        parameters.stream().filter(p -> p.getExtensions().containsKey("x-dws-filter")).collect(Collectors.toList());
    filterParams.forEach(fp -> {
      Object value = inputParams.get(fp.getName());
      String filterPath = (String) fp.getExtensions().get("x-dws-filter");
      String[] path = filterPath.split("\\.");
      Field targetField = root;
      for (int i = 1; i < path.length - 1; i++) {
        int idx = i;
        targetField =
            targetField.getChildren().stream().filter(c -> c.getName().equals(path[idx])).findFirst().orElseThrow();
      }
      targetField.setArguments(Map.of(path[path.length - 1], value));
    });
  }

  public static Field toField(@NonNull ResponseTemplate responseTemplate) {
    ResponseObject responseObject = responseTemplate.getResponseObject();
    return toField(responseObject).get(0);
  }

  private static List<Field> toField(ResponseObject responseObject) {
    if (responseObject.getSummary().isEnvelope()) {
      return envelopeToField(responseObject);
    }
    else if("array".equals(responseObject.getSummary().getSchema().getType())){
      return arrayToField(responseObject);
    }
    else {
      return nonEnvelopeToField(responseObject);
    }
  }

  private static List<Field> arrayToField(ResponseObject responseObject) {
    ResponseObject item = responseObject.getSummary().getItems().get(0);
    return toField(item);
  }

  private static List<Field> nonEnvelopeToField(ResponseObject responseObject) {
    Field result = Field.builder().name(responseObject.getIdentifier()).build();
    result.setChildren(responseObject.getSummary().getChildren().stream().filter(mapToQuery()).flatMap(child -> toField(child).stream()).collect(Collectors.toList()));
    return List.of(result);
  }

  private static List<Field> envelopeToField(ResponseObject responseObject) {
    return responseObject.getSummary().getChildren().stream().filter(mapToQuery()).flatMap(child -> toField(child).stream()).collect(Collectors.toList());
  }

  private static Predicate<ResponseObject> mapToQuery() {
    return c -> c.getSummary().isRequired() && c.getSummary().getDwsExpr() == null;
  }

  private boolean isGraphQlIdentifier(String type) {
    return type.replace("!", "")
        .replace("\\[", "")
        .replace("]", "")
        .matches("^ID$");
  }
}
