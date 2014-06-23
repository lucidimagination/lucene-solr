package org.apache.solr.security;

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

import org.apache.solr.common.auth.AuthCredentials;
import org.apache.solr.request.SolrQueryRequest;

/**
 * Responsible for dealing with Authentication Credentials for Solr-node to Solr-node requests
 * <p/>
 * The developer making code that sends Solr-node to Solr-node requests should never be "creative" and make his
 * own AuthCredentials. Basically for Solr-node to Solr-node requests there are only two options:
 * <ul>
 *   <li>
 *    either, this Solr-node to Solr-node request is a direct reaction to another request (a "super-request)
 *    and credentials for that request should be fetched by
 *    AuthCredentialsSource.useAuthCredentialsFromOuterRequest(&lt;super-request&gt;).getAuthCredentials(), potentially using information
 *    from the "super-request" for deciding on the credentials for this "sub-request" (e.g. just reusing the credentials from the
 *    super-request)
 *   </li>
 *   <li>
 *     or, this Solr-node to Solr-node request is a request that the sending Solr-node itself took the initiative to make
 *    and credentials for that request should be fetched by AuthCredentialsSource.useInternalAuthCredentials().getAuthCredentials()
 *   </li>
 * </ul>
 * This way, by plugging in (setCurrentSubRequestFactory and setCurrentInternalRequestFactory), you can control the credentials
 * strategy for Solr-node to Solr-node requests
 */
public class InterSolrNodeAuthCredentialsFactory {
  
  /**
   * An abstraction that only allows for two kinds of Authentication Credentials to be produced for requests.
   * <p/>
   * Any components designed to issue Solr-node to Solr-node requests, should use AuthCredentialsSource as the type of the parameter
   * deciding on the credentials in a specific case. This will make it clear for the developers using this component that there are
   * basically only two options, and that the developer is not supposed to get "creative" wrt coming up with AuthCredentials
   * for the Solr-node to Solr-node requests issued through this component 
   */
  public static final class AuthCredentialsSource {

    private AuthCredentials authCredentials;

    private AuthCredentialsSource(AuthCredentials authCredentials) {
      this.authCredentials = authCredentials;
    }
    
    public AuthCredentials getAuthCredentials() {
      return authCredentials;
    }

    public static AuthCredentialsSource useInternalAuthCredentials() {
      return new AuthCredentialsSource(InterSolrNodeAuthCredentialsFactory.getCurrentInternalRequestFactory().getInternalAuthCredentials());
    }

    public static AuthCredentialsSource useAuthCredentialsFromOuterRequest(SolrQueryRequest outerRequest) {
      return new AuthCredentialsSource(InterSolrNodeAuthCredentialsFactory.getCurrentSubRequestFactory().getFromOuterRequest(outerRequest));
    }

  } 
  
  protected static final AuthCredentials EMPTY_AUTH_CREDENTIALS = new AuthCredentials(null);

  // For requests issued as a direct reaction to a request coming from the "outside"
  
  // Will potentially be set to something else from CoreContainer
  private static SubRequestFactory currentSubRequestFactory = new DefaultSubRequestFactory();

  public static interface SubRequestFactory {
    AuthCredentials getFromOuterRequest(SolrQueryRequest outerRequest);
  }
  
  public static class DefaultSubRequestFactory implements SubRequestFactory {
    public AuthCredentials getFromOuterRequest(SolrQueryRequest outerRequest) {
      return EMPTY_AUTH_CREDENTIALS;
    }
  }

  public static SubRequestFactory getCurrentSubRequestFactory() {
    return currentSubRequestFactory;
  }

  public static void setCurrentSubRequestFactory(SubRequestFactory currentSubRequestFactory) {
    InterSolrNodeAuthCredentialsFactory.currentSubRequestFactory = currentSubRequestFactory;
  }
  
  // For requests issued on initiative from the solr-node itself 

  // Will potentially be set to something else from CoreContainer
  private static InternalRequestFactory currentInternalRequestFactory = new DefaultInternalRequestFactory();
  
  public static interface InternalRequestFactory {
    AuthCredentials getInternalAuthCredentials();
  }

  /**
   * Default implementation of a request factory for obtaining authentication credentials
   * from JVM properties.
   * <p/>
   * <b>WARNING</b><br/>
   * Although a simple solution, be aware that credentials given on
   * the command-line are possible to read by anyone who have access to run "ps" on the
   * server. Consider implementing your own InternalRequestFactory and plugging it in
   * through solr.xml.
   */
  public static class DefaultInternalRequestFactory implements InternalRequestFactory {
    
    @Override
    public synchronized AuthCredentials getInternalAuthCredentials() {
      return EMPTY_AUTH_CREDENTIALS;
    }
    
  }
  
  public static InternalRequestFactory getCurrentInternalRequestFactory() {
    return currentInternalRequestFactory;
  }
  
  public static void setCurrentInternalRequestFactory(InternalRequestFactory currentInternalRequestFactory) {
    InterSolrNodeAuthCredentialsFactory.currentInternalRequestFactory = currentInternalRequestFactory;
  }
  
}


