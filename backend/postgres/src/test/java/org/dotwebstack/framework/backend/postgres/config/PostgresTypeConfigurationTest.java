package org.dotwebstack.framework.backend.postgres.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.lenient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.dotwebstack.framework.core.config.AbstractTypeConfiguration;
import org.dotwebstack.framework.core.config.DotWebStackConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostgresTypeConfigurationTest {

  private static final String FIELD_IDENTIFIER = "identifier";

  private static final String FIELD_PART_OF = "partOf";

  private static final String BEER_TYPE_NAME = "Beer";

  @Mock
  private DotWebStackConfiguration dotWebStackConfiguration;

  @Mock
  Map<String, AbstractTypeConfiguration<?>> objectTypesMock;

  @BeforeEach
  public void beforeEach() {
    dotWebStackConfigurationMock();
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void init_shouldWork_withValidConfiguration() {
    JoinColumn joinColumn = createJoinColumnWithReferencedField("beer_identifier", "identifier_beer");
    JoinColumn inversedJoinColumn = createJoinColumnWithReferencedField("ingredient_code", "code");

    PostgresTypeConfiguration typeConfiguration = createTypeConfiguration(joinColumn, inversedJoinColumn);

    AbstractTypeConfiguration postgresTypeConfiguration = new PostgresTypeConfiguration();

    lenient().when(objectTypesMock.get(BEER_TYPE_NAME))
        .thenReturn(postgresTypeConfiguration);

    assertDoesNotThrow(() -> typeConfiguration.init(dotWebStackConfiguration));
  }

  @Test
  void init_shouldWork_withAggregationOfConfiguration() {
    JoinColumn joinColumn = createJoinColumnWithReferencedField("beer_identifier", "identifier_beer");
    JoinColumn inversedJoinColumn = createJoinColumnWithReferencedField("ingredient_code", "code");

    PostgresTypeConfiguration typeConfiguration =
        createTypeConfiguration(joinColumn, inversedJoinColumn, BEER_TYPE_NAME);

    assertDoesNotThrow(() -> typeConfiguration.init(dotWebStackConfiguration));
  }

  @Test
  void getReferredFields_returnsList_forFieldWithJoinTable() {
    JoinColumn joinColumn = createJoinColumnWithReferencedField("beer_identifier", "partOf");
    JoinColumn inversedJoinColumn = createJoinColumnWithReferencedField("ingredient_code", "code");

    PostgresTypeConfiguration typeConfiguration =
        createTypeConfiguration(joinColumn, inversedJoinColumn, BEER_TYPE_NAME);

    var fieldConfigurations = typeConfiguration.getReferencedFields("partOf");

    assertThat(fieldConfigurations, hasSize(1));
  }

  @Test
  void getReferredFields_returnsEmptyList_forFieldWithoutJoinTable() {
    JoinColumn joinColumn = createJoinColumnWithReferencedField("beer_identifier", "partOf");
    JoinColumn inversedJoinColumn = createJoinColumnWithReferencedField("ingredient_code", "code");

    PostgresTypeConfiguration typeConfiguration =
        createTypeConfiguration(joinColumn, inversedJoinColumn, BEER_TYPE_NAME);

    var fieldConfigurations = typeConfiguration.getReferencedFields("identifier");

    assertThat(fieldConfigurations, is(empty()));
  }

  private JoinColumn createJoinColumnWithReferencedField(String name, String fieldName) {
    JoinColumn joinColumn = new JoinColumn();
    joinColumn.setName(name);
    joinColumn.setReferencedField(fieldName);

    return joinColumn;
  }

  private PostgresTypeConfiguration createTypeConfiguration(JoinColumn joinColumn, JoinColumn inverseJoinColumn) {
    return createTypeConfiguration(joinColumn, inverseJoinColumn, null);
  }

  private PostgresTypeConfiguration createTypeConfiguration(JoinColumn joinColumn, JoinColumn inverseJoinColumn,
      String aggregationOf) {
    PostgresTypeConfiguration typeConfiguration = new PostgresTypeConfiguration();

    typeConfiguration.setKeys(List.of(FIELD_IDENTIFIER));

    JoinTable joinTable = createJoinTable(joinColumn, inverseJoinColumn);
    PostgresFieldConfiguration fieldConfiguration = createPostgresFieldConfiguration(joinTable);
    fieldConfiguration.setType("Beer");

    if (StringUtils.isNoneBlank(aggregationOf)) {
      fieldConfiguration.setAggregationOf(aggregationOf);
    }

    PostgresFieldConfiguration stringFieldConfiguration = new PostgresFieldConfiguration();
    stringFieldConfiguration.setType("String");

    Map<String, PostgresFieldConfiguration> fieldsMap =
        new HashMap<>(Map.of(FIELD_IDENTIFIER, stringFieldConfiguration, FIELD_PART_OF, fieldConfiguration));


    typeConfiguration.setFields(fieldsMap);

    typeConfiguration.setTable("db.ingredient");

    return typeConfiguration;
  }

  private JoinTable createJoinTable(JoinColumn joinColumn, JoinColumn inverseJoinColumn) {
    JoinTable joinTable = new JoinTable();
    joinTable.setName("db.beer_ingredient");

    if (joinColumn != null) {
      joinTable.setJoinColumns(List.of(joinColumn));
    }

    if (inverseJoinColumn != null) {
      joinTable.setInverseJoinColumns(List.of(inverseJoinColumn));
    }

    return joinTable;
  }

  private PostgresFieldConfiguration createPostgresFieldConfiguration(JoinTable joinTable) {
    PostgresFieldConfiguration fieldConfiguration = new PostgresFieldConfiguration();

    fieldConfiguration.setJoinTable(joinTable);

    return fieldConfiguration;
  }

  private void dotWebStackConfigurationMock() {
    lenient().when(dotWebStackConfiguration.getObjectTypes())
        .thenReturn(objectTypesMock);
    lenient().when(objectTypesMock.get(null))
        .thenReturn(null);
  }
}
