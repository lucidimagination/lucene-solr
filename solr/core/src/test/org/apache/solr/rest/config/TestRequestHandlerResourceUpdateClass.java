package org.apache.solr.rest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRequestHandlerResourceUpdateClass extends
        SolrConfigRestTestBase {

  static Logger log = LoggerFactory
          .getLogger(TestRequestHandlerResourceUpdateClass.class);

  public void testUpdateSelectClass() throws Exception {
    assertJQ("/config/requestHandlers/select", "/class=='solr.SearchHandler'",
            "/defaults/echoParams=='explicit'", "/defaults/rows==5",
            "/defaults/df=='text'", "/defaults/facet==true");

    String content = "{'class':'org.apache.solr.handler.component.SearchHandler'}";

    assertJPut("/config/requestHandlers/select", json(content),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/select",
            "/class=='org.apache.solr.handler.component.SearchHandler'",
            "/defaults/echoParams=='explicit'", "/defaults/rows==5",
            "/defaults/df=='text'", "/defaults/facet==true");
  }

  public void testUpdateGetClass() throws Exception {
    assertJQ("/config/requestHandlers/get",
            "/class=='solr.RealTimeGetHandler'",
            "/defaults/omitHeader=='true'", "/defaults/wt=='json'",
            "/defaults/indent=='true'");

    String content = "{'class':'org.apache.solr.handler.RealTimeGetHandler'}";

    assertJPut("/config/requestHandlers/get", json(content),
            "/response=='solrconfig.xml file updated'");

    assertJQ("/config/requestHandlers/get",
            "/class=='org.apache.solr.handler.RealTimeGetHandler'",
            "/defaults/omitHeader=='true'", "/defaults/wt=='json'",
            "/defaults/indent=='true'");
  }
}
