package org.apache.solr.rest.config.enumeration;

import java.util.LinkedHashMap;
import java.util.Map;

public enum SolrRequestHandlerParam {

  DEFAULTS("defaults"), APPENDS("appends"), INVARIANTS("invariants"), COMPONENTS(
          "components"), FIRST_COMPONENTS("first-components"), LAST_COMPONENTS(
          "last-components"), CLAZZ("class");

  private String value;

  private static Map<String, SolrRequestHandlerParam> requestHandlerParamNames = new LinkedHashMap<String, SolrRequestHandlerParam>();

  static {
    for (SolrRequestHandlerParam param : SolrRequestHandlerParam.values()) {
      requestHandlerParamNames.put(param.value, param);
    }
  }

  private SolrRequestHandlerParam(String value) {
    this.value = value;
  }

  public static Map<String, SolrRequestHandlerParam> getSolrRequestHandlerParam() {
    return requestHandlerParamNames;
  }

  public String getValue() {
    return value;
  }

  public static String printValues() {
    int size = requestHandlerParamNames.size(), i = 1;

    StringBuffer sb = new StringBuffer();
    
    sb.append("[");

    for (String key : requestHandlerParamNames.keySet()) {
      sb.append(key);
      if (i < size)
        sb.append(", ");
      i++;
    }
    
    sb.append("]");

    return sb.toString();
  }
}
