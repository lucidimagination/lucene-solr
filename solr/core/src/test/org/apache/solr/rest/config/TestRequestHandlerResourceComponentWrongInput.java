package org.apache.solr.rest.config;

import org.junit.Test;

public class TestRequestHandlerResourceComponentWrongInput extends SolrConfigRestTestBase {

  @Test
  public void testUpdateComponentWrongInput() throws Exception {
    
    log.info("============ testUpdateComponentWrongInput ============");
    
    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':5,'df':'text','facet':true}");

    assertJPut("/config/requestHandlers",
            json("{'/select' : {'components':['facet']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/components/[0]=='facet'");

    assertJPut("/config/requestHandlers",
            json("{'/select' : {'last-components':['query']}}"),
            "/errors=='First/Last components only valid if you do not declare components'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'components':[],'last-components':['query']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/last-components/[0]=='query'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'components':['facet'], 'first-components':[]}}"),
            "/errors=='First/Last components only valid if you do not declare components'");
  }
}
