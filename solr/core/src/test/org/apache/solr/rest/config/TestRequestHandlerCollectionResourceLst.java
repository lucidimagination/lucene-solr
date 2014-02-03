package org.apache.solr.rest.config;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRequestHandlerCollectionResourceLst extends SolrConfigRestTestBase {

  static Logger log = LoggerFactory
          .getLogger(TestRequestHandlerCollectionResource.class);

  @Test
  public void testUpdateBatchLst() throws Exception {

    log.info("============ testUpdateBatchRequestHandler ============");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':5,'df':'text','facet':true}");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'defaults':{'echoParams':'explicit','rows':10,'df':'all'}}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':10,'df':'all'}");

    assertJPut("/config/requestHandlers",
            json("{'/select' : {'invariants':{'facet':false}}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':10,'df':'all'}",
            "/invariants=={'facet':false}");

    assertJPut("/config/requestHandlers",
            json("{'/select' : {'appends':{'fq':'inStock:true'}}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/defaults=={'echoParams':'explicit','rows':10,'df':'all'}",
            "/invariants=={'facet':false}", "/appends=={'fq':'inStock:true'}");
  }
}
