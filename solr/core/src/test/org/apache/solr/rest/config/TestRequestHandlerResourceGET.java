package org.apache.solr.rest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Test;

public class TestRequestHandlerResourceGET extends SolrConfigRestTestBase {

  static Logger log = LoggerFactory
          .getLogger(TestRequestHandlerCollectionResource.class);

  @Test
  public void testGetSelectRequestHandler() throws Exception {

    // Testing the main request handlers
    assertJQ("/config/requestHandlers/select", "/class=='solr.SearchHandler'",
            "/defaults/echoParams=='explicit'", "/defaults/rows==5",
            "/defaults/df=='text'");

    assertJQ("/config/requestHandlers/query", "/class=='solr.SearchHandler'",
            "/defaults/echoParams=='explicit'", "/defaults/wt=='json'",
            "/defaults/indent=='true'", "/defaults/df=='text'");
  }

  @Test
  public void testGetSelectNonExistenRequestHandler() throws Exception {

    String nonExistentRHname = "nonexistentRH";
    assertJQ("/config/requestHandlers/" + nonExistentRHname,
            "/Error=='The request handler /" + nonExistentRHname
                    + " does not exist'");

  }

}
