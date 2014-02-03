package org.apache.solr.rest.config;

import org.junit.Test;

public class TestRequestHandlerCollectionResourceLastComponents extends
        SolrConfigRestTestBase {

  @Test
  public void updateLastComponentsRequestHandlers() throws Exception {
    log.info("============ testUpdateBatchRequestHandler ============");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':5,'df':'text','facet':true}");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'defaults':{'echoParams':'explicit','rows':10,'df':'text'},"
                    + "'last-components':['query']" + "}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':10,'df':'text'}",
            "/last-components/[0]=='query'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'defaults':{'echoParams':'explicit','rows':20,'df':'all'},"
                    + "'last-components':['query', 'debug']" + "}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':20,'df':'all'}",
            "/last-components/[0]=='query'", "/last-components/[1]=='debug'");
  }
}
