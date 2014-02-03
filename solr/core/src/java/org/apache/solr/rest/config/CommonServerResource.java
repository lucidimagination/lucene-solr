package org.apache.solr.rest.config;

import java.util.Set;

import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;
import org.restlet.Request;
import org.restlet.data.Dimension;

public interface CommonServerResource {
  Set<Dimension> getDimensions();

  Request getRequest();

  QueryResponseWriter getResponseWriter();

  SolrQueryRequest getSolrRequest();

  SolrQueryResponse getSolrResponse();
}
