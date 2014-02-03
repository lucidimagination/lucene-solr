package org.apache.solr.rest.config;

import org.junit.Test;

public class TestRequestHandlerCollectionResourceFirstComponents extends
        SolrConfigRestTestBase {

  @Test
  public void updateFirstComponentsRequestHandlers() throws Exception {
    log.info("============ testUpdateBatchRequestHandler ============");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':5,'df':'text','facet':true}");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'defaults':{'echoParams':'explicit','rows':10,'df':'text'},"
                    + "'first-components':['query']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':10,'df':'text'}",
            "/first-components/[0]=='query'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'defaults':{'echoParams':'explicit','rows':10,'df':'all'},"
                    + "'first-components':['query', 'facet']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':10,'df':'all'}",
            "/first-components/[0]=='query'", "/first-components/[1]=='facet'");
  }
}
