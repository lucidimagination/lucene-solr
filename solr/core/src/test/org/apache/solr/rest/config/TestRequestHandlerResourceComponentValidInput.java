package org.apache.solr.rest.config;

import org.junit.Test;

public class TestRequestHandlerResourceComponentValidInput extends SolrConfigRestTestBase {
  @Test
  public void testUpdateComponentValidInput() throws Exception {
    log.info("============ testUpdateComponentValidInput ============");

    assertJQ("/config/requestHandlers/select", "/class=='solr.SearchHandler'",
            "/defaults=={'echoParams':'explicit','rows':5,'df':'text','facet':true}");

    assertJPut("/config/requestHandlers",
            json("{'/select' : {'components':['facet']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/components/[0]=='facet'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'components':[],'last-components':['query'],'first-components':['facet']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/first-components/[0]=='facet'", "/last-components/[0]=='query'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'last-components':[],'first-components':[],'components':['facet']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/components/[0]=='facet'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'components':[],'last-components':['query'],'first-components':[]}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select", "/last-components/[0]=='query'");
  }
}
