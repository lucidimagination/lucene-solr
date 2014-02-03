package org.apache.solr.rest.config;

import org.junit.Test;

public class TestRequestHandlerCollectionResourceFIrstLastComponents extends
        SolrConfigRestTestBase {

  @Test
  public void updateBothComponentsRequestHandlers() throws Exception {
    log.info("============ testUpdateBatchRequestHandler ============");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':5,'df':'text','facet':true}");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'defaults':{'echoParams':'explicit','rows':10,'df':'text'},"
                    + "'first-components':['query'],'last-components':['facet']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':10,'df':'text'}",
            "/first-components/[0]=='query'", "/last-components/[0]=='facet'");

    assertJPut("/config/requestHandlers",
            json("{'/select' : {'last-components':['facet', 'debug']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':10,'df':'text'}",
            "/first-components/[0]=='query'", "/last-components/[0]=='facet'",
            "/last-components/[1]=='debug'");
  }
}
