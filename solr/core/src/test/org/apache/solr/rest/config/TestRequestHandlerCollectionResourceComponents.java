package org.apache.solr.rest.config;

import org.junit.Test;

public class TestRequestHandlerCollectionResourceComponents extends
        SolrConfigRestTestBase {

  @Test
  public void updateComponentsRequestHandlers() throws Exception {
    log.info("============ testUpdateBatchRequestHandler ============");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':5,'df':'text','facet':true}");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'defaults':{'echoParams':'explicit','rows':10,'df':'text'},"
                    + "'components':['query']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':10,'df':'text'}",
            "/components/[0]=='query'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'defaults':{'echoParams':'explicit','rows':15,'df':'text'},"
                    + "'components':['query', 'debug']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':15,'df':'text'}",
            "/components/[0]=='query'", "/components/[1]=='debug'");
  }
}
