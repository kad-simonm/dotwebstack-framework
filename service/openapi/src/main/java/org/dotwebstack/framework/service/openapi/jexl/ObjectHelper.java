package org.dotwebstack.framework.service.openapi.jexl;

import java.util.List;
import java.util.Map;

import static org.dotwebstack.framework.service.openapi.exception.ExceptionHelper.illegalArgumentException;

public class ObjectHelper {

  private ObjectHelper() {}

  public static <T> T cast(Class<T> clazz, Object value) {
    if (value == null) {
      throw new IllegalArgumentException("Object null!");
    }
    if (!(clazz.isAssignableFrom(value.getClass()))) {
      throw illegalArgumentException("Object class '{}' not instance of {}!", value.getClass()
          .getSimpleName(), clazz.getSimpleName());
    }

    return clazz.cast(value);
  }

  @SuppressWarnings("unchecked")
  public static List<Object> castToList(Object value) {
    return cast(List.class, value);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> castToMap(Object value) {
    return cast(Map.class, value);
  }
}
