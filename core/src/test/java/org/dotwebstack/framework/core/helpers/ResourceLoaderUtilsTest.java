package org.dotwebstack.framework.core.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import org.dotwebstack.framework.core.ResourceProperties;
import org.junit.jupiter.api.Test;

class ResourceLoaderUtilsTest {

  @Test
  void assert_notNull_ClassPathResource() {
    // Arrange & Act
    URI resource = ResourceLoaderUtils.getResourceLocation("assets/");

    // Assert
    assertNotNull(resource);
  }

  @Test
  void test_nonExistingResource() {
    // Arrange & Act
    URI resource = ResourceLoaderUtils.getResourceLocation("invalid/");

    // Assert
    assertNull(resource);
  }

  @Test
  void assert_notNull_FileResource() throws URISyntaxException {
    // Arrange
    URI assertUri = this.getClass()
        .getResource(ResourceProperties.getResourcePath()
            .resolve("assets/")
            .getPath())
        .toURI();

    // Act
    URI resource = ResourceLoaderUtils.getResourceLocation(assertUri.getPath());

    // Assert
    assertNotNull(resource);
  }

  @Test
  void filePath_Exists_Test() throws URISyntaxException {
    // Arrange
    URI assetsUri = this.getClass()
        .getResource(ResourceProperties.getResourcePath()
            .resolve("assets/")
            .getPath())
        .toURI();

    // Act
    boolean uriExists = ResourceLoaderUtils.uriExists(assetsUri);

    // Assert
    assertTrue(uriExists);
  }

  @Test
  void filePath_Not_Exists_Test() {
    // Arrange && Act
    boolean uriExists = ResourceLoaderUtils.uriExists(URI.create("file:/invalid_path"));

    // Assert
    assertFalse(uriExists);
  }
}
