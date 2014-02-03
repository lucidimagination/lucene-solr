package org.apache.solr.rest.config;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRequestHandlerCollectionResourceEnsureDeleteComponents  extends SolrConfigRestTestBase{
  
  static Logger log = LoggerFactory
          .getLogger(TestRequestHandlerCollectionResource.class);

  @Test
  public void testDeleteComponents() throws Throwable{
    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'components':['query']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/components/[0]=='query'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'components':[]}}"),
            "/response=='solrconfig.xml file updated'");

    assertNotExists("/config/requestHandlers/select",
            "/components==null");
  }
  
  @Test
  public void testDeleteFirstComponents() throws Exception{
    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'first-components':['query']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/first-components/[0]=='query'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'first-components':[]}}"),
            "/response=='solrconfig.xml file updated'");

    assertNotExists("/config/requestHandlers/select",
            "/first-components==null");
  }
  
  @Test
  public void testDeleteLastComponents() throws Exception{
    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'last-components':['query']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/last-components/[0]=='query'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'last-components':[]}}"),
            "/response=='solrconfig.xml file updated'");

    assertNotExists("/config/requestHandlers/select",
            "/last-components==null");
  }
  
  @Test
  public void testDeleteBothComponents() throws Exception{
    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'first-components':['query'], 'last-components':['facet']}}"),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/first-components/[0]=='query'",
            "/last-components/[0]=='facet'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'first-components':[], 'last-components':[]}}"),
            "/response=='solrconfig.xml file updated'");

    assertNotExists("/config/requestHandlers/select",
            "/first-components==null",
            "/last-components==null");
  }
}
