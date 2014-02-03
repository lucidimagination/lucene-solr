package org.apache.solr.rest.config.exception;

public class SolrConfigException extends Exception {

  private static final long serialVersionUID = 7510572451447709828L;

  public SolrConfigException() {
    super();
  }

  public SolrConfigException(String message) {
    super(message);
  }
}
