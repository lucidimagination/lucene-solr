package org.apache.solr.rest.config.enumeration;

public enum SolrPrimitiveType {

  STRING("str"), BOOLEAN("bool"), DOUBLE("double"), INTEGER("int");

  String value;

  private SolrPrimitiveType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

}
