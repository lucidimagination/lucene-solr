package org.apache.solr.rest.config;

import org.junit.Test;

public class TestRequestHandlerResourcePUT extends SolrConfigRestTestBase {

  @Test
  public void testPutRequestHandler() throws Exception {

    String content = "{"
            + "'invariants' : {'rows' : 15 , 'echoParams' : 'explicit' } ,"
            + "'defaults' : { 'rows' : 100 , 'df' : 'text' } ,"
            + "'appends' : { 'rows' : 20 } }";

    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/class=='solr.SearchHandler'",
            "/invariants/rows==15", "/invariants/echoParams=='explicit'",
            "/defaults/rows==100", "/defaults/df=='text'", "/appends/rows==20");

  }

  @Test
  public void testPutRequestHandlerWithWrongJsonStructure() throws Exception {

    // Test for defaults, appends and invariants

    // List as json body
    String content = "[ 'firstElement' , 'secondElement']";
    assertJPut(
            "/config/requestHandlers/select",
            json(content),
            "/errors/[0]/message=='Format error.  Cause: java.util.ArrayList cannot be cast to java.util.Map'",
            "/errors/[0]/object with problem==['firstElement' , 'secondElement']",
            "/errors/[0]/expected format=={'defaults' : {} , 'appends' : {} , 'invariants' : {} }");

    // Validating components
    content = "{ 'components' : 'firstElement' , 'first-components' : 'secondElement' }";
    assertJPut(
            "/config/requestHandlers/select",
            json(content),
            "/errors/[0]/message=='Format error.  Cause: java.lang.String cannot be cast to java.util.List'",
            "/errors/[0]/object with problem=='firstElement'",
            "/errors/[1]/message=='Format error.  Cause: java.lang.String cannot be cast to java.util.List'",
            "/errors/[1]/object with problem=='secondElement'");

    // Validating parameter name
    content = "{ 'wrongParamName1' : { 'name1' : 'value1' } , 'wrongParamName2' : { 'name1' : 'value1' } }";
    assertJPut(
            "/config/requestHandlers/select",
            json(content),
            "/errors/[0]/message=='Format error.  Cause: Only the following parameters are allowed: [defaults, appends, invariants, components, first-components, last-components, class]'",
            "/errors/[0]/object with problem=='wrongParamName1'",
            "/errors/[1]/message=='Format error.  Cause: Only the following parameters are allowed: [defaults, appends, invariants, components, first-components, last-components, class]'",
            "/errors/[1]/object with problem=='wrongParamName2'");

    // Validating search components list
    content = "{ 'components' : { 'name1' : 'value1' } }";
    assertJPut(
            "/config/requestHandlers/select",
            json(content),
            "/errors/[0]/message=='Format error.  Cause: java.util.LinkedHashMap cannot be cast to java.util.List'",
            "/errors/[0]/object with problem=={ 'name1' : 'value1' }",
            "/errors/[0]/expected format==['facet','mlt']");

    // Validating components content
    content = "{ 'components' : [ 1 , { 'a' : 'a' } ] }";
    assertJPut(
            "/config/requestHandlers/select",
            json(content),
            "/errors/[0]/message=='Format error.  Cause: This list can only contains string values'",
            "/errors/[0]/object with problem==[ 1 , { 'a' : 'a' } ]",
            "/errors/[0]/expected format==['facet','mlt']",
            "/errors/[1]/message=='Format error.  Cause: This list can only contains string values'",
            "/errors/[1]/object with problem==[ 1 , { 'a' : 'a' } ]",
            "/errors/[1]/expected format==['facet','mlt']");

    // Validating parameters(defaults, appends , invariants) structure
    content = "{ 'defaults' : [ 'a' , 'b' ] }";
    assertJPut(
            "/config/requestHandlers/select",
            json(content),
            "/errors/[0]/message=='Format error.  Cause: java.util.ArrayList cannot be cast to java.util.Map'",
            "/errors/[0]/object with problem==[ 'a' , 'b' ]",
            "/errors/[0]/expected format=={'echoParams' : 'all' , 'rows' : '20' , 'df' : 'title' }");

  }
}
