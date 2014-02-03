package org.apache.solr.rest.config;

import org.junit.Test;

public class TestRequestHandlerResourceIndependent extends SolrConfigRestTestBase {

  @Test
  public void testPutRequestHandler() throws Exception {
    // Test each possible update independently to test that each update will
    // affect only the parameter indicated in input json

    // updating defaults
    String content = "{"
            + "'defaults' :  { 'rows' : 100 , 'df' : 'text' , 'echoParams' : 'explicit'}"
            + "}";
    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/class=='solr.SearchHandler'",
            "/defaults/rows==100", "/defaults/df=='text'",
            "/defaults/echoParams=='explicit'");

    // updating appends and verifying previous configuration
    content = "{"
            + "'appends' : { 'rows' : 100 , 'df' : 'text' , 'echoParams' : 'explicit' }"
            + "}";
    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/class=='solr.SearchHandler'",
            "/defaults/rows==100", "/defaults/df=='text'",
            "/defaults/echoParams=='explicit'", "/appends/rows==100",
            "/appends/df=='text'", "/appends/echoParams=='explicit'");

    // updating invariants and verifying previous configuration
    content = "{"
            + "'invariants' : { 'rows' : 100 , 'df' : 'text' , 'echoParams' : 'explicit'}"
            + "}";
    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/class=='solr.SearchHandler'",
            "/defaults/rows==100}", "/defaults/df=='text'",
            "/defaults/echoParams=='explicit'", "/appends/rows==100",
            "/appends/df=='text'", "/appends/echoParams=='explicit'",
            "/invariants/rows==100", "/invariants/df=='text'",
            "/invariants/echoParams=='explicit'");

    // updating components and verifying previous configuration
    content = "{" + " 'components' : [ 'facet' , 'mlt']" + "}";
    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/class=='solr.SearchHandler'",
            "/defaults/rows==100", "/defaults/df=='text'",
            "/defaults/echoParams=='explicit'", "/appends/rows==100",
            "/appends/df=='text'", "/appends/echoParams=='explicit'",
            "/invariants/rows==100", "/invariants/df=='text'",
            "/invariants/echoParams=='explicit'", "/components/[0]=='facet'",
            "/components/[1]=='mlt'");

  }
}
