package org.dotwebstack.framework.backend.json.directives;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import org.dotwebstack.framework.backend.json.query.JsonDataService;
import org.dotwebstack.framework.backend.json.query.JsonQueryFetcher;
import org.dotwebstack.framework.core.InvalidConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.support.ResourcePatternResolver;

@ExtendWith(MockitoExtension.class)
class JsonDirectivesWiringTest {

  @Mock
  private ResourcePatternResolver resourceLoaderMock;

  @Mock
  private GraphQLFieldDefinition graphQlFieldDefinitionMock;

  @Mock
  private GraphQLObjectType graphQlOutputTypeMock;

  @Mock
  private GraphQLScalarType wrongGraphQlOutputTypeMock;

  @Mock
  private GraphQLDirective jsonDirectiveMock;

  @Mock
  private SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environmentMock;

  @Mock
  private GraphQLCodeRegistry.Builder codeRegistryBuilderMock;

  @Mock
  private GraphQLArgument graphQlFileArgumentMock;

  @Mock
  private GraphQLArgument graphQlPathArgumentMock;

  private JsonDirectiveWiring jsonDirectiveWiring;

  @BeforeEach
  void setup() {
    JsonDataService jsonDataService = new JsonDataService(resourceLoaderMock);
    JsonQueryFetcher jsonQueryFetcher = new JsonQueryFetcher(jsonDataService);
    jsonDirectiveWiring = new JsonDirectiveWiring(jsonQueryFetcher);
  }

  @Test
  void getDirectiveNameTest() {
    // Act
    String directiveName = jsonDirectiveWiring.getDirectiveName();

    // Assert
    assertThat(directiveName, equalTo(JsonDirectives.JSON_NAME));
  }

  @Test
  void onFieldSuccessTest() {
    // Arrange
    GraphQLArgument graphQlArgument = mock(GraphQLArgument.class);
    GraphQLDirective predicateDirective = mock(GraphQLDirective.class);
    GraphQLNonNull graphQlNonNull = mock(GraphQLNonNull.class);

    when(graphQlArgument.getType()).thenReturn(graphQlNonNull);

    when(environmentMock.getFieldDefinition()).thenReturn(graphQlFieldDefinitionMock);

    when(graphQlArgument.getDirective(PredicateDirectives.PREDICATE_NAME)).thenReturn(predicateDirective);
    when(graphQlFieldDefinitionMock.getArguments()).thenReturn(Collections.singletonList(graphQlArgument));

    when(graphQlFileArgumentMock.getValue()).thenReturn("test.json");
    when(graphQlPathArgumentMock.getValue()).thenReturn("$.beers[?]");

    ArrayList<String> predicates = new ArrayList<>();
    predicates.add("args.identifier");

    when(environmentMock.getElement()).thenReturn(graphQlFieldDefinitionMock);
    when(environmentMock.getDirective()).thenReturn(jsonDirectiveMock);

    when(graphQlFieldDefinitionMock.getType()).thenReturn(graphQlOutputTypeMock);
    when(environmentMock.getCodeRegistry()).thenReturn(codeRegistryBuilderMock);
    when(jsonDirectiveMock.getName()).thenReturn(JsonDirectives.JSON_NAME);
    when(jsonDirectiveMock.getArgument(JsonDirectives.ARGS_FILE)).thenReturn(graphQlFileArgumentMock);
    when(jsonDirectiveMock.getArgument(JsonDirectives.ARGS_PATH)).thenReturn(graphQlPathArgumentMock);

    // Act
    assertDoesNotThrow(() -> jsonDirectiveWiring.onField(environmentMock));
  }

  @Test
  void validateOutputTypeThrowsIllegalArgumentExceptionTest() {
    // Arrange
    when(environmentMock.getElement()).thenReturn(graphQlFieldDefinitionMock);
    when(graphQlFieldDefinitionMock.getType()).thenReturn(graphQlOutputTypeMock);
    when(graphQlFieldDefinitionMock.getType()).thenReturn(wrongGraphQlOutputTypeMock);

    // Act
    assertThrows(IllegalArgumentException.class, () -> jsonDirectiveWiring.onField(environmentMock));
  }

  @Test
  void validateDirectiveNameThrowsInvalidConfigurationExceptionTest() {
    // Arrange
    when(environmentMock.getElement()).thenReturn(graphQlFieldDefinitionMock);
    when(environmentMock.getDirective()).thenReturn(jsonDirectiveMock);
    when(graphQlFieldDefinitionMock.getType()).thenReturn(graphQlOutputTypeMock);
    when(jsonDirectiveMock.getName()).thenReturn("WRONG");

    // Act
    assertThrows(InvalidConfigurationException.class, () -> jsonDirectiveWiring.onField(environmentMock));
  }

  @Test
  void predicatesPathMismatchThrowsInvalidConfigurationExceptionTest() {
    // Arrange
    when(environmentMock.getElement()).thenReturn(graphQlFieldDefinitionMock);
    when(environmentMock.getDirective()).thenReturn(jsonDirectiveMock);
    when(environmentMock.getFieldDefinition()).thenReturn(graphQlFieldDefinitionMock);
    when(graphQlFieldDefinitionMock.getType()).thenReturn(graphQlOutputTypeMock);
    when(graphQlFileArgumentMock.getValue()).thenReturn("test.json");
    when(graphQlPathArgumentMock.getValue()).thenReturn("$..beers[?]");

    GraphQLArgument graphQlArgument = mock(GraphQLArgument.class);
    GraphQLDirective predicateDirective = mock(GraphQLDirective.class);

    when(graphQlArgument.getName()).thenReturn("identifier");
    when(graphQlArgument.getDirective(PredicateDirectives.PREDICATE_NAME)).thenReturn(predicateDirective);

    when(jsonDirectiveMock.getName()).thenReturn(JsonDirectives.JSON_NAME);
    when(jsonDirectiveMock.getArgument(JsonDirectives.ARGS_FILE)).thenReturn(graphQlFileArgumentMock);
    when(jsonDirectiveMock.getArgument(JsonDirectives.ARGS_PATH)).thenReturn(graphQlPathArgumentMock);
    when(graphQlFieldDefinitionMock.getArguments()).thenReturn(Collections.singletonList(graphQlArgument));

    // Act
    assertThrows(InvalidConfigurationException.class, () -> jsonDirectiveWiring.onField(environmentMock));
  }
}