package org.apache.solr.rest.config;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRequestHandlerResourceMultiplePath extends
        SolrConfigRestTestBase {

  static Logger log = LoggerFactory
          .getLogger(TestRequestHandlerResourceMultiplePath.class);

  @Test
  public void testMultiplePath() throws Exception {
    assertJQ("/config/requestHandlers/update/json",
            "/class=='solr.JsonUpdateRequestHandler'",
            "/defaults/stream.contentType=='application/json'");

    assertJQ("/config/requestHandlers/update/csv",
            "/class=='solr.CSVRequestHandler'",
            "/defaults/stream.contentType=='application/csv'");

    assertJQ("/config/requestHandlers/update/extract",
            "/class=='solr.extraction.ExtractingRequestHandler'",
            "/defaults/lowernames=='true'", "/defaults/uprefix=='ignored_'",
            "/defaults/captureAttr=='true'", "/defaults/fmap.a=='links'",
            "/defaults/fmap.div=='ignored_'");

    assertJQ("/config/requestHandlers/analysis/field",
            "/class=='solr.FieldAnalysisRequestHandler'");

    assertJQ("/config/requestHandlers/admin/ping",
            "/class=='solr.PingRequestHandler'",
            "/invariants/q=='solrpingquery'", "/defaults/echoParams=='all'");

    assertJQ("/config/requestHandlers/admin/",
            "/class=='solr.admin.AdminHandlers'");
  }
}
