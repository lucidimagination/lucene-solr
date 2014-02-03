package org.apache.solr.rest.config.cloud;

import org.apache.solr.util.RestTestHarness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRequestHandlerResourceCloudWrongComponents extends
        SolrCloudConfigRestTestBase {

  private static final Logger log = LoggerFactory
          .getLogger(TestRequestHandlerResourceCloud.class);

  public TestRequestHandlerResourceCloudWrongComponents() {
    super(4, 8, true);
  }

  @Override
  public void doTest() throws Exception {

    setupHarnesses();

    RestTestHarness publisher = restTestHarnesses.get(r
            .nextInt(restTestHarnesses.size()));

    String request = "/config/requestHandlers/select";
    String response = publisher.query(request);

    String content = "{ 'components' : [ 'nonExistenComponent' ] } ";

    response = publisher.put(request, json(content));
    long updateRequestHandlerTime = System.currentTimeMillis();

    if (!validate(response,
            "/errors=='Unknown Search Component: nonExistenComponent'"))
      fail("Error in update request handler");

    int maxAttempts = 20;
    long retryPauseMillis = 20;

  }
}
