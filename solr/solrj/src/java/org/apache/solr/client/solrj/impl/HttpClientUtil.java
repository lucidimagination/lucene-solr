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
package org.apache.solr.client.solrj.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Observable;
import java.util.Observer;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientParamBean;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.SystemDefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager; // jdoc
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.auth.AuthCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for creating/configuring httpclient instances. 
 */
public class HttpClientUtil {
  
  public static final String HTTP_CLIENTS_MUST_ADAPT_TO_CREDENTIALS_CHANGES = "httpClientsMustAdaptToCredentialsChanges";
  // socket timeout measured in ms, closes a socket if read
  // takes longer than x ms to complete. throws
  // java.net.SocketTimeoutException: Read timed out exception
  public static final String PROP_SO_TIMEOUT = "socketTimeout";
  // connection timeout measures in ms, closes a socket if connection
  // cannot be established within x ms. with a
  // java.net.SocketTimeoutException: Connection timed out
  public static final String PROP_CONNECTION_TIMEOUT = "connTimeout";
  // Maximum connections allowed per host
  public static final String PROP_MAX_CONNECTIONS_PER_HOST = "maxConnectionsPerHost";
  // Maximum total connections allowed
  public static final String PROP_MAX_CONNECTIONS = "maxConnections";
  // Retry http requests on error
  public static final String PROP_USE_RETRY = "retry";
  // Allow compression (deflate,gzip) if server supports it
  public static final String PROP_ALLOW_COMPRESSION = "allowCompression";
  // Follow redirects
  public static final String PROP_FOLLOW_REDIRECTS = "followRedirects";
  /**
   * @deprecated Please use the generic {@link AuthCredentials} instead. This parameter will go away
   */
  @Deprecated
  public static final String PROP_BASIC_AUTH_USER = "httpBasicAuthUser";
  /**
   * @deprecated Please use the generic {@link AuthCredentials} instead. This parameter will go away
   */
  @Deprecated
  public static final String PROP_BASIC_AUTH_PASS = "httpBasicAuthPassword";

  private static final Logger logger = LoggerFactory
      .getLogger(HttpClientUtil.class);
  
  static final DefaultHttpRequestRetryHandler NO_RETRY = new DefaultHttpRequestRetryHandler(
      0, false);

  private static HttpClientConfigurer configurer = new HttpClientConfigurer();
  
  private HttpClientUtil(){}
  
  /**
   * Replace the {@link HttpClientConfigurer} class used in configuring the http
   * clients with a custom implementation.
   */
  public static void setConfigurer(HttpClientConfigurer newConfigurer) {
    configurer = newConfigurer;
  }
  
  public static HttpClientConfigurer getConfigurer() {
    return configurer;
  }
  
  /**
   * Creates new http client by using the provided configuration.
   * 
   * @param params
   *          http client configuration, if null a client with default
   *          configuration (no additional configuration) is created. 
   */
  public static HttpClient createClient(final SolrParams params) {
    return createClient(params, null, null);
    }

  /**
   * Creates new http client by using the provided configuration.
   * 
   * @param params
   *          http client configuration, if null a client with default
   *          configuration (no additional configuration) is created.
   * @param authCredentials Credentials to be used for all requests issued through the HttpClient (null allowed) 
   */
  public static HttpClient createClient(final SolrParams params, AuthCredentials authCredentials) {
    return createClient(params, null, authCredentials);
  }
  
  /**
   * Creates new http client by using the provided configuration.
   * 
   * @param params
   *          http client configuration, if null a client with default
   *          configuration (no additional configuration) is created that uses
   * @param cm Connection-manager to be used
   */
  public static HttpClient createClient(final SolrParams params, ClientConnectionManager cm) {
    return createClient(params, cm, null);
  }

  /**
   * Creates new http client by using the provided configuration.
   * 
   * @param params
   *          http client configuration, if null a client with default
   *          configuration (no additional configuration) is created that uses
   * @param cm Connection-manager to be used
   * @param authCredentials Credentials to be used for all requests issued through the HttpClient (null allowed)
   */
  public static HttpClient createClient(final SolrParams params, ClientConnectionManager cm, AuthCredentials authCredentials) {
    final ModifiableSolrParams config = new ModifiableSolrParams(params);
    if (logger.isDebugEnabled()) {
      logger.debug("Creating new http client, config:" + config);
    }
    HttpClient httpClient;
    if (cm != null) {
      final ClientConnectionManager mgrToUse = cm;
      httpClient = new SystemDefaultHttpClient() {
        @Override
        protected  ClientConnectionManager createClientConnectionManager() {
          return mgrToUse;
        }
      };
    } else {
      httpClient = new SystemDefaultHttpClient();
    }
    if (Boolean.getBoolean(HTTP_CLIENTS_MUST_ADAPT_TO_CREDENTIALS_CHANGES) && authCredentials != null) {
      httpClient = new CredentialsObservingHttpClientWrapper((DefaultHttpClient)httpClient, authCredentials);
    }
    configureClient(httpClient, config, authCredentials);
    return httpClient;
  }

  /**
   * Configures {@link DefaultHttpClient}, only sets parameters if they are
   * present in config.
   */
  public static void configureClient(final DefaultHttpClient httpClient,
      SolrParams config) {
    configurer.configure(httpClient, config, null);
  }

  public static void configureClient(final HttpClient httpClient,
      SolrParams config, AuthCredentials authCredentials) {
    configurer.configure(httpClient, config, authCredentials);
  }

  private static HttpClient getAcutalHttpClient(HttpClient httpClient) {
    if (httpClient instanceof CredentialsObservingHttpClientWrapper) return ((CredentialsObservingHttpClientWrapper)httpClient).getWrappedHttpClient();
    return httpClient;
  }
  
  private static DefaultHttpClient getDefaultHttpClient(HttpClient httpClient) {
    httpClient = getAcutalHttpClient(httpClient);
    if (httpClient instanceof DefaultHttpClient) return (DefaultHttpClient)httpClient;
    return null;
  }
  
  public static CredentialsProvider getCredentialsProvider(HttpClient httpClient) {
    DefaultHttpClient defaultHttpClient = getDefaultHttpClient(httpClient);
    if (defaultHttpClient != null) {
      return defaultHttpClient.getCredentialsProvider();
    }    
    return null;
  }

  /**
   * Control HTTP payload compression.
   * 
   * @param allowCompression
   *          true will enable compression (needs support from server), false
   *          will disable compression.
   */
  public static void setAllowCompression(HttpClient httpClient,
      boolean allowCompression) {
    DefaultHttpClient defaultHttpClient = getDefaultHttpClient(httpClient);
    if (defaultHttpClient != null) {
      defaultHttpClient
        .removeRequestInterceptorByClass(UseCompressionRequestInterceptor.class);
      defaultHttpClient
        .removeResponseInterceptorByClass(UseCompressionResponseInterceptor.class);
    if (allowCompression) {
        defaultHttpClient.addRequestInterceptor(new UseCompressionRequestInterceptor());
        defaultHttpClient
          .addResponseInterceptor(new UseCompressionResponseInterceptor());
    }
    } else {
      throw new RuntimeException("Setting allow-compression not allowed for " + HttpClient.class.getName() + " not based on " + DefaultHttpClient.class.getName());
  }
  }

  /**
   * Set http basic auth information. If basicAuthUser or basicAuthPass is null
   * the basic auth configuration is cleared. Currently this is not preemtive
   * authentication. So it is not currently possible to do a post request while
   * using this setting.
   * @deprecated Please use {@link #setAuthCredentials(org.apache.http.client.HttpClient, org.apache.solr.common.auth.AuthCredentials)} instead.
   */
  @Deprecated
  public static void setBasicAuth(DefaultHttpClient httpClient,
      String basicAuthUser, String basicAuthPass) {
    if (basicAuthUser != null && basicAuthPass != null) {
      setAuthCredentials(httpClient, AuthCredentials.createBasicAuthCredentials(basicAuthUser, basicAuthPass));
    } else {
      AuthCredentials.clearCredentials(httpClient.getCredentialsProvider());
    }
  }

  /**
   * Set http authentication credentials on the HttpClient to be used for all subsequent
   * requests issued through this HttpClient. If it already had credentials set they will be cleared.
   * @since solr 5.0
   */
  public static void setAuthCredentials(HttpClient httpClient, AuthCredentials authCredentials) {
    if (authCredentials != null) {
      if (httpClient instanceof CredentialsObservingHttpClientWrapper) {
        ((CredentialsObservingHttpClientWrapper)httpClient).setAuthCredentials(authCredentials);
    } else {
        authCredentials.applyToHttpClient(httpClient);
    }
    } else {
      AuthCredentials.clearCredentials(((DefaultHttpClient)httpClient).getCredentialsProvider());
  }
  }

  /**
   * Set max connections allowed per host. This call will only work when
   * {@link ThreadSafeClientConnManager} or
   * {@link PoolingClientConnectionManager} is used.
   */
  public static void setMaxConnectionsPerHost(HttpClient httpClient,
      int max) {
    // would have been nice if there was a common interface
    if (httpClient.getConnectionManager() instanceof ThreadSafeClientConnManager) {
      ThreadSafeClientConnManager mgr = (ThreadSafeClientConnManager)httpClient.getConnectionManager();
      mgr.setDefaultMaxPerRoute(max);
    } else if (httpClient.getConnectionManager() instanceof PoolingClientConnectionManager) {
      PoolingClientConnectionManager mgr = (PoolingClientConnectionManager)httpClient.getConnectionManager();
      mgr.setDefaultMaxPerRoute(max);
    }
  }

  /**
   * Set max total connections allowed. This call will only work when
   * {@link ThreadSafeClientConnManager} or
   * {@link PoolingClientConnectionManager} is used.
   */
  public static void setMaxConnections(final HttpClient httpClient,
      int max) {
    // would have been nice if there was a common interface
    if (httpClient.getConnectionManager() instanceof ThreadSafeClientConnManager) {
      ThreadSafeClientConnManager mgr = (ThreadSafeClientConnManager)httpClient.getConnectionManager();
      mgr.setMaxTotal(max);
    } else if (httpClient.getConnectionManager() instanceof PoolingClientConnectionManager) {
      PoolingClientConnectionManager mgr = (PoolingClientConnectionManager)httpClient.getConnectionManager();
      mgr.setMaxTotal(max);
    }
  }
  

  /**
   * Defines the socket timeout (SO_TIMEOUT) in milliseconds. A timeout value of
   * zero is interpreted as an infinite timeout.
   * 
   * @param timeout timeout in milliseconds
   */
  public static void setSoTimeout(HttpClient httpClient, int timeout) {
    HttpConnectionParams.setSoTimeout(httpClient.getParams(),
        timeout);
  }

  /**
   * Control retry handler 
   * @param useRetry when false the client will not try to retry failed requests.
   */
  public static void setUseRetry(final HttpClient httpClient,
      boolean useRetry) {
    DefaultHttpClient defaultHttpClient = getDefaultHttpClient(httpClient);
    if (defaultHttpClient != null) {
    if (!useRetry) {
        defaultHttpClient.setHttpRequestRetryHandler(NO_RETRY);
    } else {
        defaultHttpClient.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler());
    }
    } else {
      throw new RuntimeException("Setting allow-compression not allowed for " + HttpClient.class.getName() + " not based on " + DefaultHttpClient.class.getName());
  }
  }

  /**
   * Set connection timeout. A timeout value of zero is interpreted as an
   * infinite timeout.
   * 
   * @param timeout
   *          connection Timeout in milliseconds
   */
  public static void setConnectionTimeout(final HttpClient httpClient,
      int timeout) {
      HttpConnectionParams.setConnectionTimeout(httpClient.getParams(),
          timeout);
  }

  /**
   * Set follow redirects.
   *
   * @param followRedirects  When true the client will follow redirects.
   */
  public static void setFollowRedirects(HttpClient httpClient,
      boolean followRedirects) {
    new ClientParamBean(httpClient.getParams()).setHandleRedirects(followRedirects);
  }

  private static class UseCompressionRequestInterceptor implements
      HttpRequestInterceptor {
    
    @Override
    public void process(HttpRequest request, HttpContext context)
        throws HttpException, IOException {
      if (!request.containsHeader("Accept-Encoding")) {
        request.addHeader("Accept-Encoding", "gzip, deflate");
      }
    }
  }
  
  private static class UseCompressionResponseInterceptor implements
      HttpResponseInterceptor {
    
    @Override
    public void process(final HttpResponse response, final HttpContext context)
        throws HttpException, IOException {
      
      HttpEntity entity = response.getEntity();
      Header ceheader = entity.getContentEncoding();
      if (ceheader != null) {
        HeaderElement[] codecs = ceheader.getElements();
        for (int i = 0; i < codecs.length; i++) {
          if (codecs[i].getName().equalsIgnoreCase("gzip")) {
            response
                .setEntity(new GzipDecompressingEntity(response.getEntity()));
            return;
          }
          if (codecs[i].getName().equalsIgnoreCase("deflate")) {
            response.setEntity(new DeflateDecompressingEntity(response
                .getEntity()));
            return;
          }
        }
      }
    }
  }
  
  private static class GzipDecompressingEntity extends HttpEntityWrapper {
    public GzipDecompressingEntity(final HttpEntity entity) {
      super(entity);
    }
    
    @Override
    public InputStream getContent() throws IOException, IllegalStateException {
      return new GZIPInputStream(wrappedEntity.getContent());
    }
    
    @Override
    public long getContentLength() {
      return -1;
    }
  }
  
  private static class DeflateDecompressingEntity extends
      GzipDecompressingEntity {
    public DeflateDecompressingEntity(final HttpEntity entity) {
      super(entity);
    }
    
    @Override
    public InputStream getContent() throws IOException, IllegalStateException {
      return new InflaterInputStream(wrappedEntity.getContent());
    }
  }
  
  private static class CredentialsObservingHttpClientWrapper implements HttpClient, Observer {
    
    private final DefaultHttpClient wrappedHttpClient;
    private AuthCredentials authCredentials;
    
    public CredentialsObservingHttpClientWrapper(DefaultHttpClient wrappedHttpClient, AuthCredentials authCredentials) {
      if (wrappedHttpClient == null) throw new RuntimeException(getClass() + " cannot be instantiated without a HttpClient to wrap");
      if (authCredentials == null) throw new RuntimeException(getClass() + " cannot be instantiated without a AuthCredentials to observe");
      this.wrappedHttpClient = wrappedHttpClient;
      setAuthCredentials(authCredentials);
}
    
    private Object authCredentialsSyncObject = new Object();
    
    private void setAuthCredentials(AuthCredentials authCredentials) {
      synchronized (authCredentialsSyncObject) {
        if (this.authCredentials != null) this.authCredentials.deleteObserver(this); 
        this.authCredentials = authCredentials;
        if (authCredentials != null) {
          authCredentials.addObserver(this);
          authCredentials.applyToHttpClient(wrappedHttpClient);
        } else {
          wrappedHttpClient.getCredentialsProvider().clear();
        }
      }
    }
    
    @Override
    public void update(Observable observable, Object object) {
      synchronized (authCredentialsSyncObject) {
        if (this.authCredentials == observable) {
          this.authCredentials.applyToHttpClient(wrappedHttpClient);
        }
      }
    }
    
    private DefaultHttpClient getWrappedHttpClient() {
      return wrappedHttpClient;
    }

    @Override
    public HttpResponse execute(HttpUriRequest request) throws IOException,
        ClientProtocolException {
      return wrappedHttpClient.execute(request);
    }
    
    @Override
    public HttpResponse execute(HttpUriRequest request, HttpContext context)
        throws IOException, ClientProtocolException {
      return wrappedHttpClient.execute(request, context);
    }
    
    @Override
    public HttpResponse execute(HttpHost host, HttpRequest request)
        throws IOException, ClientProtocolException {
      return wrappedHttpClient.execute(host, request);
    }
    
    @Override
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler)
        throws IOException, ClientProtocolException {
      return wrappedHttpClient.execute(request, responseHandler);
    }
    
    @Override
    public HttpResponse execute(HttpHost host, HttpRequest request, HttpContext context)
        throws IOException, ClientProtocolException {
      return wrappedHttpClient.execute(host, request, context);
    }
    
    @Override
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> handler,
        HttpContext context) throws IOException, ClientProtocolException {
      return wrappedHttpClient.execute(request, handler, context);
    }
    
    @Override
    public <T> T execute(HttpHost host, HttpRequest request,
        ResponseHandler<? extends T> handler) throws IOException,
        ClientProtocolException {
      return wrappedHttpClient.execute(host, request, handler);
    }
    
    @Override
    public <T> T execute(HttpHost host, HttpRequest request,
        ResponseHandler<? extends T> handler, HttpContext context) throws IOException,
        ClientProtocolException {
      return wrappedHttpClient.execute(host, request, handler, context);
    }
    
    @Override
    public ClientConnectionManager getConnectionManager() {
      return wrappedHttpClient.getConnectionManager();
    }
    
    @Override
    public HttpParams getParams() {
      return wrappedHttpClient.getParams();
    }
    
  }
  
}
