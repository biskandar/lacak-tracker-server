/*
 * Copyright 2015 - 2016 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.web;

import java.beans.Introspector;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.traccar.helper.Log;
import org.traccar.model.MiscFormatter;

public final class JsonConverter {
  
  private JsonConverter() {
  }
  
  private static final DateTimeFormatter DATE_FORMAT = ISODateTimeFormat
      .dateTime();
  
  public static Date parseDate(String value) {
    return DATE_FORMAT.parseDateTime(value).toDate();
  }
  
  public static <T> T objectFromJson(Reader reader, Class<T> clazz)
      throws ParseException {
    try (JsonReader jsonReader = Json.createReader(reader)) {
      return objectFromJson(jsonReader.readObject(), clazz);
    }
  }
  
  public static <T> T objectFromJson(JsonObject json, Class<T> clazz) {
    try {
      T object = clazz.newInstance();
      Method[] methods = object.getClass().getMethods();
      return objectFromJson(json, object, methods);
    } catch (InstantiationException | IllegalAccessException e) {
      throw new IllegalArgumentException();
    }
  }
  
  private static <T> T objectFromJson(JsonObject json, T object,
      Method[] methods) {
    for (final Method method : methods) {
      if (method.getName().startsWith("set")
          && method.getParameterTypes().length == 1) {
        
        final String name = Introspector.decapitalize(method.getName()
            .substring(3));
        Class<?> parameterType = method.getParameterTypes()[0];
        
        if (json.containsKey(name) && !json.isNull(name)) {
          try {
            if (parameterType.equals(boolean.class)) {
              method.invoke(object, json.getBoolean(name));
            } else if (parameterType.equals(int.class)) {
              method.invoke(object, json.getJsonNumber(name).intValue());
            } else if (parameterType.equals(long.class)) {
              if (json.get(name).getValueType() == JsonValue.ValueType.NUMBER) {
                method.invoke(object, json.getJsonNumber(name).longValue());
              }
            } else if (parameterType.equals(double.class)) {
              method.invoke(object, json.getJsonNumber(name).doubleValue());
            } else if (parameterType.equals(String.class)) {
              method.invoke(object, json.getString(name));
            } else if (parameterType.equals(Date.class)) {
              method.invoke(object,
                  DATE_FORMAT.parseDateTime(json.getString(name)).toDate());
            } else if (parameterType.equals(Map.class)) {
              method.invoke(object,
                  MiscFormatter.fromJson(json.getJsonObject(name)));
            }
          } catch (IllegalAccessException | InvocationTargetException error) {
            Log.warning(error);
          }
        }
      }
    }
    return object;
  }
  
  public static <T> JsonObject objectToJson(T object) {
    
    JsonObjectBuilder json = Json.createObjectBuilder();
    
    Method[] methods = object.getClass().getMethods();
    
    for (Method method : methods) {
      if (method.getName().startsWith("get")
          && method.getParameterTypes().length == 0) {
        String name = Introspector.decapitalize(method.getName().substring(3));
        try {
          if (method.getReturnType().equals(boolean.class)) {
            json.add(name, (Boolean) method.invoke(object));
          } else if (method.getReturnType().equals(int.class)) {
            json.add(name, (Integer) method.invoke(object));
          } else if (method.getReturnType().equals(long.class)) {
            json.add(name, (Long) method.invoke(object));
          } else if (method.getReturnType().equals(double.class)) {
            json.add(name, (Double) method.invoke(object));
          } else if (method.getReturnType().equals(String.class)) {
            String value = (String) method.invoke(object);
            if (value != null) {
              json.add(name, value);
            }
          } else if (method.getReturnType().equals(Date.class)) {
            Date value = (Date) method.invoke(object);
            if (value != null) {
              json.add(name, DATE_FORMAT.print(new DateTime(value)));
            }
          } else if (method.getReturnType().equals(Map.class)) {
            Map value = (Map) method.invoke(object);
            if (value != null) {
              json.add(name, MiscFormatter.toJson(value));
            }
          } else if (method.getReturnType().equals(List.class)) {
            List value = (List) method.invoke(object);
            if (value != null) {
              json.add(name, arrayToJson(value));
            }
          }
        } catch (IllegalAccessException | InvocationTargetException error) {
          Log.warning(error);
        }
      }
    }
    
    return json.build();
  }
  
  public static JsonArray arrayToJson(Collection<?> array) {
    
    JsonArrayBuilder json = Json.createArrayBuilder();
    
    for (Object object : array) {
      switch (object.getClass().getSimpleName().toLowerCase()) {
      case "string":
        json.add(object.toString());
        break;
      case "long":
        json.add((long) object);
        break;
      case "double":
        json.add((double) object);
        break;
      case "boolean":
        json.add((boolean) object);
        break;
      default:
        json.add(objectToJson(object));
        break;
      }
    }
    
    return json.build();
  }
  
}
