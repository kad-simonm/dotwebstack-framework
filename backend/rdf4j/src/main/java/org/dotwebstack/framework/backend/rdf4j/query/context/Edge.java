package org.dotwebstack.framework.backend.rdf4j.query.context;

import java.util.Objects;
import lombok.Builder;
import lombok.Data;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfPredicate;


@Data
@Builder
class Edge implements Comparable<Edge> {

  private RdfPredicate predicate;

  private RdfPredicate constructPredicate;

  private Vertice object;

  private boolean isOptional;

  private boolean isVisible;

  private Aggregate aggregate;

  RdfPredicate getConstructPredicate() {
    return Objects.nonNull(constructPredicate) ? constructPredicate : predicate;
  }

  @Override
  public int compareTo(Edge other) {
    return Boolean.compare(isOptional, other.isOptional);
  }

  public String toString() {
    String value = object.getSubject() != null ? object.getSubject()
        .getQueryString()
        : object.getIris()
            .iterator()
            .next()
            .getQueryString();
    return predicate.getQueryString() + " " + value;
  }
}
