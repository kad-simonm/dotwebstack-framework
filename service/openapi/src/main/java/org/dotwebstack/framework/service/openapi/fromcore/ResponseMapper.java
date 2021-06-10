package org.dotwebstack.framework.service.openapi.fromcore;

import org.springframework.util.MimeType;

public interface ResponseMapper {

  boolean supportsOutputMimeType(MimeType mimeType);

  boolean supportsInputObjectClass(Class<?> clazz);

  String toResponse(Object input);

}
