package org.dotwebstack.framework.core.query.model.filter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode
@SuperBuilder
@ToString
public class EqualsFilterCriteria implements FilterCriteria {
  private final FieldPath fieldPath;

  @Getter
  private final Object value;

  @Override
  public FieldPath getFieldPath() {
    return fieldPath;
  }
}
