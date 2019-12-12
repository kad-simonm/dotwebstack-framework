package org.dotwebstack.framework.service.openapi.mapping;

import java.util.List;
import java.util.Objects;
import lombok.NonNull;
import org.dotwebstack.framework.service.openapi.response.ResponseWriteContext;
import org.dotwebstack.framework.service.openapi.response.SchemaSummary;

class ResponseMapperHelper {

  private ResponseMapperHelper() {}

  static boolean isRequiredOrExpandedAndNullOrEmpty(@NonNull ResponseWriteContext writeContext, Object object,
      boolean expanded) {
    return (writeContext.getResponseObject()
        .getSummary()
        .isRequired() || expanded) && ((Objects.isNull(object))
            || isEmptyList(writeContext.getResponseObject()
                .getSummary(), object));
  }

  private static boolean isEmptyList(SchemaSummary summary, Object object) {
    if (summary.isNillable() && object instanceof List) {
      return ((List) object).isEmpty();
    }
    return false;
  }
}
