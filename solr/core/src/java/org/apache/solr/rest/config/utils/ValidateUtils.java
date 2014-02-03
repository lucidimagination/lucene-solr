package org.apache.solr.rest.config.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.solr.rest.config.enumeration.SolrPrimitiveType;
import org.apache.solr.rest.config.enumeration.SolrRequestHandlerParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class ValidateUtils {

  private static final int BATCH_UPDATE = 1;
  private static final int SINGLE_UPDATE = 2;
  private static final String ERROR_MESSAGE = "message";
  private static final String EXPECTED_FORMAT_ERROR_MESSAGE = "expected format";
  private static final String PROBLEMOBJECT_ERROR_MESSAGE = "object with problem";
  private static final Logger log = LoggerFactory
          .getLogger(ValidateUtils.class);

  private static Map<String, Object> jsonExample;
  static {
    jsonExample = new LinkedHashMap<String, Object>();
    jsonExample.put(SolrRequestHandlerParam.DEFAULTS.getValue(),
            new HashMap<String, Object>());
    jsonExample.put(SolrRequestHandlerParam.APPENDS.getValue(),
            new HashMap<String, Object>());
    jsonExample.put(SolrRequestHandlerParam.INVARIANTS.getValue(),
            new HashMap<String, Object>());
  }

  private static List<String> jsonComponentsExample;
  static {
    jsonComponentsExample = new ArrayList<String>();
    jsonComponentsExample.add("facet");
    jsonComponentsExample.add("mlt");
  }

  private static Map<String, Object> jsonSingleParamExample;
  static {
    jsonSingleParamExample = new LinkedHashMap<String, Object>();
    jsonSingleParamExample.put("echoParams", "all");
    jsonSingleParamExample.put("rows", "20");
    jsonSingleParamExample.put("df", "title");
  }

  private static Map<String, Object> jsonBatchExample;
  static {
    jsonBatchExample = new LinkedHashMap<String, Object>();
    jsonBatchExample.put("/select", new HashMap<String, Object>());
    jsonBatchExample.put("/query", new HashMap<String, Object>());
  }

  @SuppressWarnings("unchecked")
  private static void validateComplexMap(Object object,
          List<Map<String, Object>> errors, int fork) {
    try {
      Map<String, Object> map = (Map<String, Object>) object;

      switch (fork) {
      case BATCH_UPDATE:
        iterateBatchUpdateObject(map, errors);
        break;
      case SINGLE_UPDATE:
        iterateSingleUpdateObject(map, errors);
        break;
      }
    } catch (Throwable t) {
      switch (fork) {
      case SINGLE_UPDATE:
        errors.add(buildErrorMessage("Format error.  Cause: " + t.getMessage(),
                object, jsonExample));
        break;
      case BATCH_UPDATE:
        errors.add(buildErrorMessage("Format error.  Cause: " + t.getMessage(),
                object, jsonBatchExample));
        break;
      }
    }
  }

  private static void iterateSingleUpdateObject(Map<String, Object> map,
          List<Map<String, Object>> errors) {
    if (!validateComponents(map)) {
      errors.add(buildErrorMessage(
              "First/Last components only valid if you do not declare components",
              null, null));
      return;
    }

    for (Entry<String, Object> entry : map.entrySet()) {
      validateRequestHandlerParamName(entry.getKey(), errors);
      if (isRequestHandlerComponent(entry.getKey()))
        validateStringList(entry.getValue(), errors);
      else if (entry.getValue() instanceof String && entry.getKey().equals("class"))
        continue;
      else
        validatePropertiesMap(entry.getValue(), errors);
    }
  }

  public static boolean validateArrCombination(boolean components,
          boolean first, boolean last) {
    return (components && !first && !last) || (!components && first && last)
            || (!components && (first || last))
            || (!components && !first && !last);
  }

  @SuppressWarnings("unchecked")
  private static boolean validateComponents(Map<String, Object> map) {

    boolean components = false;
    boolean first = false;
    boolean last = false;

    for (Entry<String, Object> entry : map.entrySet()) {

      if (!(entry.getValue() instanceof List))
        continue;
      
      List<String> data = (List<String>) entry.getValue();

      if (entry.getKey().equals(SolrRequestHandlerParam.COMPONENTS.getValue()) && !data.isEmpty()) {
        components = true;
      }
      if (entry.getKey().equals(
              SolrRequestHandlerParam.FIRST_COMPONENTS.getValue()) && !data.isEmpty()) {
        first = true;
      }
      if (entry.getKey().equals(
              SolrRequestHandlerParam.LAST_COMPONENTS.getValue()) && !data.isEmpty()) {
        last = true;
      }
    }

    return validateArrCombination(components, first, last);
  }

  @SuppressWarnings("unchecked")
  private static void validateStringList(Object value,
          List<Map<String, Object>> errors) {
    try {
      List<Object> tmp = (List<Object>) value;

      for (Object obj : tmp) {
        if (getParamDataType(obj) != SolrPrimitiveType.STRING)
          errors.add(buildErrorMessage(
                  "Format error.  Cause: This list can only contains string values",
                  value, jsonComponentsExample));
      }
    } catch (Throwable t) {

      errors.add(buildErrorMessage("Format error.  Cause: " + t.getMessage(),
              value, jsonComponentsExample));
    }
  }

  private static void iterateBatchUpdateObject(Map<String, Object> map,
          List<Map<String, Object>> errors) {
    for (Entry<String, Object> entry : map.entrySet()) {

      validateSingleUpdateObject(entry.getValue(), errors);

    }
  }

  public static boolean validateBatchUpdateObject(Object object,
          List<Map<String, Object>> errors) {

    validateComplexMap(object, errors, BATCH_UPDATE);

    return errors.size() > 0 ? false : true;
  }

  public static boolean validateSingleUpdateObject(Object object,
          List<Map<String, Object>> errors) {

    validateComplexMap(object, errors, SINGLE_UPDATE);

    return errors.size() > 0 ? false : true;
  }

  @SuppressWarnings("unchecked")
  private static void validatePropertiesMap(Object value,
          List<Map<String, Object>> errors) {
    try {
      Map<String, Object> tmp = (Map<String, Object>) value;

    } catch (Throwable t) {
      errors.add(buildErrorMessage("Format error.  Cause: " + t.getMessage(),
              value, jsonSingleParamExample));
    }
  }

  public static SolrPrimitiveType getParamDataType(Object data) {

    if (data instanceof Boolean)
      return SolrPrimitiveType.BOOLEAN;
    else if (data instanceof String)
      return SolrPrimitiveType.STRING;
    else if (data instanceof Integer || data instanceof Long)
      return SolrPrimitiveType.INTEGER;
    else if (data instanceof Double)
      return SolrPrimitiveType.DOUBLE;
    return null;
  }

  public static boolean isRequestHandlerComponent(String name) {
    if (name.equalsIgnoreCase(SolrRequestHandlerParam.COMPONENTS.getValue())
            || name.equalsIgnoreCase(SolrRequestHandlerParam.FIRST_COMPONENTS
                    .getValue())
            || name.equalsIgnoreCase(SolrRequestHandlerParam.LAST_COMPONENTS
                    .getValue()))
      return true;

    return false;
  }

  @SuppressWarnings("rawtypes")
  private static boolean componentStatus(Document document, String component,
          String handlerName, Map<String, Object> data) {
    
    boolean inXml = XMLUtils.getRequestHandlerArrNode(handlerName, component, document) != null;
    boolean inJson = data.get(component) != null;
    boolean inJsonEmpty = inJson && ((List) data.get(component)).isEmpty();
    boolean inJsonNotEmpty = inJson && !((List) data.get(component)).isEmpty();
        
    if(inXml && inJsonEmpty) {
      return false;
    } else {
      return  (inXml && inJsonNotEmpty) || inJsonNotEmpty || (inXml && !inJson);
    }    
  }

  public static boolean validateXmlComponents(String handlerName,
          Map<String, Object> data, Document document) {

    boolean components = componentStatus(document,
            SolrRequestHandlerParam.COMPONENTS.getValue(), handlerName, data);
    boolean first = componentStatus(document,
            SolrRequestHandlerParam.FIRST_COMPONENTS.getValue(), handlerName,
            data);
    boolean last = componentStatus(document,
            SolrRequestHandlerParam.LAST_COMPONENTS.getValue(), handlerName,
            data);

    return ValidateUtils.validateArrCombination(components, first, last);
  }

  private static void validateRequestHandlerParamName(String key,
          List<Map<String, Object>> errors) {
    if (SolrRequestHandlerParam.getSolrRequestHandlerParam().get(key) == null)
      errors.add(buildErrorMessage(
              "Format error.  Cause: Only the following parameters are allowed: "
                      + SolrRequestHandlerParam.printValues(), key, null));
  }

  public static Map<String, Object> buildErrorMessage(String message,
          Object problem, Object expected) {
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    response.put(ERROR_MESSAGE, message);
    if (problem != null)
      response.put(PROBLEMOBJECT_ERROR_MESSAGE, problem);
    if (expected != null)
      response.put(EXPECTED_FORMAT_ERROR_MESSAGE, expected);
    return response;
  }
}
