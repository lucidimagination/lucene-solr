package org.apache.solr.rest.config;

import org.junit.Test;

public class TestRequestHandlerResourceRemoveParams extends
        SolrConfigRestTestBase {

  @Test
  public void testRemoveDefaults() throws Exception {

    // Adding defaults
    String content = "{ 'defaults' : { 'rows' : 100 , 'df' : 'text' } }";
    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/class=='solr.SearchHandler'",
            "/defaults/rows==100", "/defaults/df=='text'");

    // Removing defaults
    content = "{ 'defaults' : {} }";
    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertNotExists("/config/requestHandlers/select", "/defaults==null");

  }

  @Test
  public void testRemoveAppends() throws Exception {

    // Adding appends
    String content = "{ 'appends' : { 'rows' : 100 , 'df' : 'text' } }";
    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/class=='solr.SearchHandler'",
            "/appends/rows==100", "/appends/df=='text'");

    // Removing appends
    content = "{ 'appends' : {} }";
    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertNotExists("/config/requestHandlers/select", "/appends==null");

  }

  @Test
  public void testRemoveInvariants() throws Exception {

    // Adding invariants
    String content = "{ 'invariants' : { 'rows' : 100 , 'df' : 'text' } }";
    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/class=='solr.SearchHandler'",
            "/invariants/rows==100", "/invariants/df=='text'");

    // Removing invariants
    content = "{ 'invariants' : {} }";
    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertNotExists("/config/requestHandlers/select", "/invariants==null");

  }
}
