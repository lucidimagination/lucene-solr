package org.apache.solr;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;

import org.apache.http.client.HttpClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.StreamingResponseCallback;
import org.apache.solr.client.solrj.embedded.JettySolrRunnerWithBasicAuth;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.auth.AuthCredentials;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.servlet.security.RegExpAuthorizationFilter;
import org.apache.solr.servlet.security.RegExpAuthorizationFilter.RegExpPatternAndRoles;

public class TestSolrServers {

  private static final List<RegExpPatternAndRoles> authorizationConstraints = 
      RegExpAuthorizationFilter.getOrderedAuthorizationConstraints(JettySolrRunnerWithBasicAuth.regExpSecurityFilterInitParams, null);
  protected static void manipulateRequest( final SolrRequest request ) {
    if (BaseDistributedSearchTestCase.RUN_WITH_COMMON_SECURITY) {
      if (request.getAuthCredentials() == null) {
        String path = request.getPath();
        for (RegExpPatternAndRoles regExpPatternAndRoles : authorizationConstraints) {
          Matcher matcher = regExpPatternAndRoles.regExpPattern.matcher(path);
          if (matcher.find()) {
            request.setAuthCredentials(JettySolrRunnerWithBasicAuth.getAuthCredentialsForRole(regExpPatternAndRoles.roles[0]));
            return;
          }
        }
      }
    }
  };
  
  @SuppressWarnings("serial")
  public static class TestHttpSolrServer extends HttpSolrServer {

    public TestHttpSolrServer(String baseURL) {
      super(baseURL);
    }

    // Add public versions of the protected convenience methods from SolrServer
    public UpdateResponse add(Collection<SolrInputDocument> docs, int commitWithinMs, AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.add(docs, commitWithinMs, authCredentials);
    }
    public UpdateResponse addBeans(Collection <?> beans, int commitWithinMs, AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.addBeans(beans, commitWithinMs, authCredentials);
    }
    public UpdateResponse add(SolrInputDocument doc, int commitWithinMs, AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.add(doc, commitWithinMs, authCredentials);
    }
    public UpdateResponse commit( boolean waitFlush, boolean waitSearcher, boolean softCommit, AuthCredentials authCredentials ) throws SolrServerException, IOException {
      return super.commit(waitFlush, waitSearcher, softCommit, authCredentials);
    }
    public UpdateResponse optimize(boolean waitFlush, boolean waitSearcher, int maxSegments, AuthCredentials authCredentials ) throws SolrServerException, IOException {
      return super.optimize(waitFlush, waitSearcher, maxSegments, authCredentials);
    }
    public UpdateResponse rollback(AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.rollback(authCredentials);
    }
    public UpdateResponse deleteById(String id, int commitWithinMs, AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.deleteById(id, commitWithinMs, authCredentials);
    }
    public UpdateResponse deleteById(List<String> ids, int commitWithinMs, AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.deleteById(ids, commitWithinMs, authCredentials);
    }
    public UpdateResponse deleteByQuery(String query, int commitWithinMs, AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.deleteByQuery(query, commitWithinMs, authCredentials);
    }
    public SolrPingResponse ping(AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.ping(authCredentials);
    }
    public QueryResponse query(SolrParams params, SolrRequest.METHOD method, AuthCredentials authCredentials) throws SolrServerException {
      return super.query(params, method, authCredentials);
    }
    public QueryResponse queryAndStreamResponse( SolrParams params, StreamingResponseCallback callback, AuthCredentials authCredentials ) throws SolrServerException, IOException {
      return super.queryAndStreamResponse(params, callback, authCredentials);
    }

    @Override
    protected void manipulateRequest( final SolrRequest request ) {
      super.manipulateRequest(request);
      TestSolrServers.manipulateRequest(request);
    }
    
  }
  
  @SuppressWarnings("serial")
  public static class TestCloudSolrServer extends CloudSolrServer {

    public TestCloudSolrServer(String zkHost) throws MalformedURLException {
      super(zkHost);
    }
    
    public TestCloudSolrServer(String zkHost, boolean updatesToLeaders) throws MalformedURLException {
      super(zkHost, updatesToLeaders);
    }

    // Add public versions of the protected convenience methods from SolrServer
    public UpdateResponse add(Collection<SolrInputDocument> docs, int commitWithinMs, AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.add(docs, commitWithinMs, authCredentials);
    }
    public UpdateResponse addBeans(Collection <?> beans, int commitWithinMs, AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.addBeans(beans, commitWithinMs, authCredentials);
    }
    public UpdateResponse add(SolrInputDocument doc, int commitWithinMs, AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.add(doc, commitWithinMs, authCredentials);
    }
    public UpdateResponse commit( boolean waitFlush, boolean waitSearcher, boolean softCommit, AuthCredentials authCredentials ) throws SolrServerException, IOException {
      return super.commit(waitFlush, waitSearcher, softCommit, authCredentials);
    }
    public UpdateResponse optimize(boolean waitFlush, boolean waitSearcher, int maxSegments, AuthCredentials authCredentials ) throws SolrServerException, IOException {
      return super.optimize(waitFlush, waitSearcher, maxSegments, authCredentials);
    }
    public UpdateResponse rollback(AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.rollback(authCredentials);
    }
    public UpdateResponse deleteById(String id, int commitWithinMs, AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.deleteById(id, commitWithinMs, authCredentials);
    }
    public UpdateResponse deleteById(List<String> ids, int commitWithinMs, AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.deleteById(ids, commitWithinMs, authCredentials);
    }
    public UpdateResponse deleteByQuery(String query, int commitWithinMs, AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.deleteByQuery(query, commitWithinMs, authCredentials);
    }
    public SolrPingResponse ping(AuthCredentials authCredentials) throws SolrServerException, IOException {
      return super.ping(authCredentials);
    }
    public QueryResponse query(SolrParams params, SolrRequest.METHOD method, AuthCredentials authCredentials) throws SolrServerException {
      return super.query(params, method, authCredentials);
    }
    public QueryResponse queryAndStreamResponse( SolrParams params, StreamingResponseCallback callback, AuthCredentials authCredentials ) throws SolrServerException, IOException {
      return super.queryAndStreamResponse(params, callback, authCredentials);
    }

    @Override
    protected void manipulateRequest( final SolrRequest request ) {
      super.manipulateRequest(request);
      TestSolrServers.manipulateRequest(request);
    }

  }

  @SuppressWarnings("serial")
  public static class TestConcurrentUpdateSolrServer extends ConcurrentUpdateSolrServer {

    public TestConcurrentUpdateSolrServer(String solrServerUrl, int queueSize, int threadCount) {
      super(solrServerUrl, queueSize, threadCount);
    }
    
    public TestConcurrentUpdateSolrServer(String solrServerUrl, HttpClient client, int queueSize, int threadCount) {
      super(solrServerUrl, client, queueSize, threadCount);
    }

    @Override
    protected void manipulateRequest( final SolrRequest request ) {
      super.manipulateRequest(request);
      TestSolrServers.manipulateRequest(request);
    }

  }

}


