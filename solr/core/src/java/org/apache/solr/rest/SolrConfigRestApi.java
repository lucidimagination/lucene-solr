package org.apache.solr.rest;

import org.apache.solr.rest.config.RequestHandlerCollectionResource;
import org.apache.solr.rest.config.RequestHandlerResource;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.routing.Router;
import org.restlet.routing.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolrConfigRestApi extends Application {

  public static final Logger log = LoggerFactory
          .getLogger(SolrConfigRestApi.class);
  private Router router;

  public static String HANDLER_NAME = "handlerName";

  public SolrConfigRestApi() {
    router = new Router(getContext());
  }

  @Override
  public void stop() throws Exception {
    if (null != router) {
      router.stop();
    }
  }

  /**
   * Bind URL paths to the appropriate ServerResource subclass.
   */
  @Override
  public synchronized Restlet createInboundRoot() {
    log.info("config createInboundRoot started");

    router.attach("/requestHandlers", RequestHandlerCollectionResource.class);
    router.attach("/requestHandlers/", RequestHandlerResource.class,
            Template.MODE_STARTS_WITH);

    log.info("config createInboundRoot complete");

    return router;
  }
}