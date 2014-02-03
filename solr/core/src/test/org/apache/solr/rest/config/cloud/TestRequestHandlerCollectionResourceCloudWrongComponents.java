package org.apache.solr.rest.config.cloud;

import org.apache.solr.util.RestTestHarness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRequestHandlerCollectionResourceCloudWrongComponents extends
        SolrCloudConfigRestTestBase {

  private static final Logger log = LoggerFactory
          .getLogger(TestRequestHandlerCollectionResourceCloud.class);

  public TestRequestHandlerCollectionResourceCloudWrongComponents() {
    super(4, 8, true);
  }

  @Override
  public void doTest() throws Exception {

    setupHarnesses();

    RestTestHarness publisher = restTestHarnesses.get(r
            .nextInt(restTestHarnesses.size()));

    String request = "/config/requestHandlers";
    String response = publisher.query(request);

    String content = "{ '/select' : { 'components' : [ 'nonExistenComponent' ] } ,"
            + "'/query' : { 'first-components' : [ 'nonExistenComponent' ] } }";

    response = publisher.put(request, json(content));
    long updateRequestHandlerTime = System.currentTimeMillis();

    if (!validate(response,
            "/errors=='Unknown Search Component: nonExistenComponent'"))
      fail("Error in update request handler");

  }
}
