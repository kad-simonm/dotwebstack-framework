package org.dotwebstack.framework.service.openapi.response;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.Objects;
import org.dotwebstack.framework.service.openapi.helper.RequestBodyResolver;

import static org.dotwebstack.framework.service.openapi.exception.ExceptionHelper.illegalArgumentException;

public class RequestBodyContextBuilder {

  private final OpenAPI openApi;

  public RequestBodyContextBuilder(OpenAPI openApi) {
    this.openApi = openApi;
  }

  public RequestBodyContext buildRequestBodyContext(RequestBody requestBody) {
    if (requestBody != null) {
      var resolvedRequestBody = RequestBodyResolver.resolveRequestBody(openApi, requestBody);
      validate(resolvedRequestBody);
      return new RequestBodyContext(resolvedRequestBody);
    } else {
      return null;
    }
  }

  static void validate(RequestBody requestBody) {
    if (Objects.isNull(requestBody.getContent())) {
      throw illegalArgumentException("RequestBody without content!");
    }

    var mediaType = requestBody.getContent()
        .get(org.springframework.http.MediaType.APPLICATION_JSON.toString());

    if (Objects.isNull(mediaType)) {
      throw illegalArgumentException("Media type '{}' not found on request body.",
          org.springframework.http.MediaType.APPLICATION_JSON.toString());
    }
  }
}
