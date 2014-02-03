package org.apache.solr.rest.config;

import org.junit.Test;

public class TestRequestHandlerResourceComponents extends SolrConfigRestTestBase {

  @Test
  public void testPutRequestHandlerWithComponents() throws Exception {

    String content = "{"
            + " 'invariants' : { 'rows' : 10 , 'echoParams' : 'explicit' } ,"
            + " 'defaults' : { 'rows' : 100  , 'df' : 'text' } ,"
            + " 'appends' : { 'rows' : 20 } ,"
            + " 'components' : [ 'facet' , 'query' ]" + " }";
    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select","/class=='solr.SearchHandler'", "/invariants/rows==10}",
            "/invariants/echoParams=='explicit'", "/defaults/rows==100",
            "/defaults/df=='text'", "/appends/rows==20",
            "/components/[0]=='facet'", "/components/[1]=='query'");

  }

  @Test
  public void testPutRequestHandlerWithWrongComponents() throws Exception {
    // Test for components, first-components and last-components

    // Three present
    String content = "{ 'components' : [ 'query' ] ,  'first-components' : [ 'facet' ]  , 'last-components' : [ 'mlt' ] }";
    assertJPut(
            "/config/requestHandlers/select",
            json(content),
            "/errors/[0]/message=='First/Last components only valid if you do not declare components'");

    // Present components and first-components
    content = "{ 'components' : [ 'query' ],  'first-components' : [ 'facet' ] }";
    assertJPut(
            "/config/requestHandlers/select",
            json(content),
            "/errors/[0]/message=='First/Last components only valid if you do not declare components'");

    // Present components and last-components
    content = "{ 'components' : [ 'query' ], 'last-components' : [ 'mlt' ] }";
    assertJPut(
            "/config/requestHandlers/select",
            json(content),
            "/errors/[0]/message=='First/Last components only valid if you do not declare components'");
  }

}
