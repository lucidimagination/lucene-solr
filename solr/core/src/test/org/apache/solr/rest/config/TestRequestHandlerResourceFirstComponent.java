package org.apache.solr.rest.config;

import org.junit.Test;

public class TestRequestHandlerResourceFirstComponent extends
        SolrConfigRestTestBase {

  //
  @Test
  public void testPutRequestHandlerWithFirstComponents() throws Exception {

    String content = "{"
            + "'invariants' : { 'rows' : 10  , 'echoParams' : 'explicit' } ,"
            + "'defaults' : { 'rows' : 100  , 'df' : 'text' } ,"
            + "'appends' : { 'rows' : 20 } ,"
            + "'first-components' : [ 'facet' , 'query' ]}";
    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/class=='solr.SearchHandler'",
            "/invariants/rows==10}", "/invariants/echoParams=='explicit'}",
            "/defaults/rows==100}", "/defaults/df=='text'",
            "/appends/rows==20}", "/first-components/[0]=='facet'",
            "/first-components/[1]=='query'");

  }
}
