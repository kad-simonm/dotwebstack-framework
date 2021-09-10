package org.dotwebstack.framework.ext.rml.mapping;

import com.taxonic.carml.engine.rdf.RdfRmlMapper;
import com.taxonic.carml.model.TriplesMap;
import java.util.Map;
import java.util.Set;
import org.dotwebstack.framework.service.openapi.HttpMethodOperation;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

@Component
public class NQuadsRmlResponseMapper extends AbstractRmlResponseMapper {

  static final MimeType N_QUADS_MEDIA_TYPE = MimeType.valueOf("application/n-quads");

  NQuadsRmlResponseMapper(RdfRmlMapper rmlMapper, Map<HttpMethodOperation, Set<TriplesMap>> mappingsPerOperation,
      Set<Namespace> namespaces) {
    super(rmlMapper, mappingsPerOperation, namespaces);
  }

  @Override
  MimeType supportedMimeType() {
    return N_QUADS_MEDIA_TYPE;
  }

  @Override
  RDFFormat rdfFormat() {
    return RDFFormat.NQUADS;
  }
}
