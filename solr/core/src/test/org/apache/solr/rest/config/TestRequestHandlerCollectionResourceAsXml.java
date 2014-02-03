package org.apache.solr.rest.config;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRequestHandlerCollectionResourceAsXml extends SolrConfigRestTestBase {

  static Logger log = LoggerFactory
          .getLogger(TestRequestHandlerCollectionResource.class);

  public void testRequestHandlerCollectionResourceAsXml() {
    assertQ("/config/requestHandlers?wt=xml",
            "(/response/arr[@name='requestHandlers']/lst)[1]/str[@name='name'][.='/select']",
            "(/response/arr[@name='requestHandlers']/lst)[1]/str[@name='class'][.='solr.SearchHandler']",
            "(/response/arr[@name='requestHandlers']/lst)[1]/lst[@name='defaults']/str[@name='echoParams'][.='explicit']",
            "(/response/arr[@name='requestHandlers']/lst)[1]/lst[@name='defaults']/int[@name='rows'][.='5']",
            "(/response/arr[@name='requestHandlers']/lst)[1]/lst[@name='defaults']/str[@name='df'][.='text']",
            "(/response/arr[@name='requestHandlers']/lst)[1]/lst[@name='defaults']/bool[@name='facet'][.='true']",
            "(/response/arr[@name='requestHandlers']/lst)[2]/str[@name='name'][.='/query']",
            "(/response/arr[@name='requestHandlers']/lst)[2]/str[@name='class'][.='solr.SearchHandler']",
            "(/response/arr[@name='requestHandlers']/lst)[2]/lst[@name='defaults']/str[@name='echoParams'][.='explicit']",
            "(/response/arr[@name='requestHandlers']/lst)[2]/lst[@name='defaults']/str[@name='wt'][.='json']",
            "(/response/arr[@name='requestHandlers']/lst)[2]/lst[@name='defaults']/str[@name='indent'][.='true']",
            "(/response/arr[@name='requestHandlers']/lst)[2]/lst[@name='defaults']/str[@name='df'][.='text']",
            "(/response/arr[@name='requestHandlers']/lst)[3]/str[@name='name'][.='/get']",
            "(/response/arr[@name='requestHandlers']/lst)[3]/str[@name='class'][.='solr.RealTimeGetHandler']",
            "(/response/arr[@name='requestHandlers']/lst)[3]/lst[@name='defaults']/str[@name='omitHeader'][.='true']",
            "(/response/arr[@name='requestHandlers']/lst)[3]/lst[@name='defaults']/str[@name='wt'][.='json']",
            "(/response/arr[@name='requestHandlers']/lst)[3]/lst[@name='defaults']/str[@name='indent'][.='true']");
  }
}
