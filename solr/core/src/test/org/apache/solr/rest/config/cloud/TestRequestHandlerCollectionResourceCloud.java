package org.apache.solr.rest.config.cloud;

import org.apache.solr.util.RestTestHarness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRequestHandlerCollectionResourceCloud extends
        SolrCloudConfigRestTestBase {

  private static final Logger log = LoggerFactory
          .getLogger(TestRequestHandlerCollectionResourceCloud.class);

  public TestRequestHandlerCollectionResourceCloud() {
    super(4, 8, true);
  }

  @Override
  public void doTest() throws Exception {

    setupHarnesses();

    RestTestHarness publisher = restTestHarnesses.get(restTestHarnesses.size()-1);

    String request = "/config/requestHandlers";
    String response = publisher.query(request);

    String content = "{ '/select' : { 'defaults' : { 'echoParams' : 'all' , 'rows' : 20 , 'df' : 'title' } , "
            + " 'appends' : { 'rows' : 15 } , "
            + "'invariants' : { 'wt' : 'json' } , 'components' : [ 'facet' ] } }, "
            + "'/query' : { 'defaults' : { 'echoParams' : 'all' , 'rows' : 20 , 'df' : 'title' } , "
            + " 'appends' : { 'rows' : 15 } , "
            + "'invariants' : { 'wt' : 'json' } , 'components' : [ 'mlt' ] } } } ";

    response = publisher.put(request, json(content));
    long updateRequestHandlerTime = System.currentTimeMillis();

    if (!validate(response, "/response=='solrconfig.xml file updated'"))
      fail("Error in update request handler");

    int maxAttempts = 20;
    long retryPauseMillis = 20;

    String requestForSelect = "/config/requestHandlers/select";
    String requestForQuery = "/config/requestHandlers/query";
    for (RestTestHarness client : restTestHarnesses) {
      
      if(client == publisher)
        continue;
      
      boolean stillTrying = true;
      for (int attemptNum = 1; stillTrying && attemptNum <= maxAttempts; ++attemptNum) {
        response = client.query(requestForSelect);
        long elapsedTimeMillis = System.currentTimeMillis()
                - updateRequestHandlerTime;

        if (validate(response, "/defaults/echoParams=='all'",
                "/defaults/rows==20", "/defaults/df=='title'",
                "/appends/rows==15", "/invariants/wt=='json'",
                "/components/[0]=='facet'")) {
          stillTrying = false;
          log.info("Server " + client.getBaseURL() + " updated at attemp "
                  + attemptNum);
        } else {
          if (attemptNum == maxAttempts) {
            log.info("Server " + client.getBaseURL() + "was not updated after "
                    + attemptNum + " attepms");
            fail("Server " + client.getBaseURL() + "was not updated after "
                    + attemptNum + " attepms");
          }
          log.info("Server " + client.getBaseURL()
                  + " without last changes at attemp " + attemptNum);
        }

        Thread.sleep(retryPauseMillis);

      }

      for (int attemptNum = 1; stillTrying && attemptNum <= maxAttempts; ++attemptNum) {
        response = client.query(requestForQuery);
        long elapsedTimeMillis = System.currentTimeMillis()
                - updateRequestHandlerTime;

        if (validate(response, "/defaults/echoParams=='all'",
                "/defaults/rows==20", "/defaults/df=='title'",
                "/appends/rows==15", "/invariants/wt=='json  '",
                "/components/[0]=='mlt'")) {
          stillTrying = false;
          log.info("Server " + client.getBaseURL() + " updated at attemp "
                  + attemptNum);
        } else {
          if (attemptNum == maxAttempts) {
            log.info("Server " + client.getBaseURL() + "was not updated after "
                    + attemptNum + " attepms");
            fail("Server " + client.getBaseURL() + "was not updated after "
                    + attemptNum + " attepms");
          }
          log.info("Server " + client.getBaseURL()
                  + " without last changes at attemp " + attemptNum);
        }
        Thread.sleep(retryPauseMillis);
      }
    }
  }
}
