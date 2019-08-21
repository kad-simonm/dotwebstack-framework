package org.dotwebstack.framework.service.openapi.query;

import static org.dotwebstack.framework.core.helpers.TypeHelper.getTypeString;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_EXPANDED_PARAMS;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import lombok.NonNull;
import org.dotwebstack.framework.core.query.GraphQlField;

public class GraphQlQueryBuilder {

  public String toQuery(@NonNull GraphQlField graphQlField, Map<String, Object> inputParams) {
    StringBuilder builder = new StringBuilder();
    StringJoiner joiner = new StringJoiner(",", "{", "}");
    StringJoiner argumentJoiner = new StringJoiner(",");

    addToQuery(graphQlField, joiner, argumentJoiner, inputParams, true, "");
    builder.append("query Wrapper");
    if (!argumentJoiner.toString()
        .isEmpty()) {
      builder.append("(");
      builder.append(argumentJoiner);
      builder.append(")");
    }
    builder.append(joiner.toString());
    return builder.toString();
  }

  private void addToQuery(GraphQlField field, StringJoiner joiner, StringJoiner headerArgumentJoiner,
      Map<String, Object> inputParams, boolean isTopLevel, String path) {
    StringJoiner argumentJoiner = new StringJoiner(",", "(", ")");
    argumentJoiner.setEmptyValue("");
    if (!field.getArguments()
        .isEmpty() && isTopLevel) {
      field.getArguments()
          .stream()
          .filter(graphQlArgument -> inputParams.containsKey(graphQlArgument.getName()))
          .forEach(graphQlArgument -> {
            argumentJoiner.add(graphQlArgument.getName() + ": $" + graphQlArgument.getName());
            headerArgumentJoiner.add("$" + graphQlArgument.getName() + ": " + getTypeString(graphQlArgument.getType()));
          });
    }

    if (!field.getFields()
        .isEmpty() && (isTopLevel || isExpanded(inputParams, path))) {
      StringJoiner childJoiner = new StringJoiner(",", "{", "}");
      field.getFields()
          .forEach(childField -> {
            String childPath = (path.isEmpty() ? "" : path + ".") + childField.getName();
            addToQuery(childField, childJoiner, headerArgumentJoiner, inputParams, false, childPath);
          });
      joiner.add(field.getName() + argumentJoiner.toString() + childJoiner.toString());
    } else if (field.getFields()
        .isEmpty()) {
      joiner.add(field.getName() + argumentJoiner.toString());
    }
  }

  @SuppressWarnings("unchecked")
  private boolean isExpanded(Map<String, Object> inputParams, String path) {
    List<String> expandVariables = (List<String>) inputParams.get(X_DWS_EXPANDED_PARAMS);
    if (Objects.nonNull(expandVariables)) {
      return expandVariables.stream()
          .anyMatch(path::equals);
    }
    return false;
  }
}