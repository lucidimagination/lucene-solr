package org.apache.solr.rest.config;

import org.junit.Test;

public class TestRequestHandlerResourceAsXml extends SolrConfigRestTestBase {
  @Test
  public void testRequestHandlerResourceAsXml() {
    assertQ("/config/requestHandlers/select?wt=xml",
            "/response/str[@name='class'][.='solr.SearchHandler']",
            "/response/lst[@name='defaults']/str[@name='echoParams'][.='explicit']",
            "/response/lst[@name='defaults']/int[@name='rows'][.='5']",
            "/response/lst[@name='defaults']/str[@name='df'][.='text']",
            "/response/lst[@name='defaults']/bool[@name='facet'][.='true']");
  }
}
