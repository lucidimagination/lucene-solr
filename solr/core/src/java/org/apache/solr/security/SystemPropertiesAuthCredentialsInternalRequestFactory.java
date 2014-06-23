package org.apache.solr.security;

import java.util.HashSet;
import java.util.Set;

import org.apache.solr.common.auth.AuthCredentials;
import org.apache.solr.common.auth.AuthCredentials.AbstractAuthMethod;
import org.apache.solr.security.InterSolrNodeAuthCredentialsFactory.InternalRequestFactory;

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

/**
 * This InternalRequestFactory picks up basic authentication credentials from System properties.
 * It is important that all Solr-nodes in the cluster are initialized with exactly the same
 * system properties.
 * <p/>
 * <b>WARNING</b><br/>
 * Although a simple solution, be aware that credentials given on
 * the command-line are possible to read by anyone who have access to run "ps" on the
 * server. Consider implementing your own InternalRequestFactory and plugging it in
 * through solr.xml.
 */
public class SystemPropertiesAuthCredentialsInternalRequestFactory implements InternalRequestFactory {
  
  public static final String USERNAME_SYS_PROP_NAME = "solr.auth.user";
  public static final String USERNAME_SYS_PROP_PASSWORD = "solr.auth.pass";
  
  private boolean alreadyDone = false;
  private AuthCredentials internalAuthCredentials = null;

  @Override
  public synchronized AuthCredentials getInternalAuthCredentials() {
    if (!alreadyDone) {
      String internalBasicAuthUsername = System.getProperty(USERNAME_SYS_PROP_NAME);
      String internalBasicAuthPassword = System.getProperty(USERNAME_SYS_PROP_PASSWORD);
      
      Set<AbstractAuthMethod> authMethods = new HashSet<>();
      if (internalBasicAuthUsername != null && internalBasicAuthPassword != null) {
        authMethods.add(new AuthCredentials.BasicHttpAuth(internalBasicAuthUsername, internalBasicAuthPassword));
      }
      if (internalAuthCredentials == null) {
        internalAuthCredentials = new AuthCredentials(authMethods);
      } else {
        // Not creating a new instance but replacing auth-methods in order to have the changes propagate
        // to objects already using and observing it
        internalAuthCredentials.setAuthMethods(authMethods);
      }
      alreadyDone = true;
    }
    return internalAuthCredentials;
  }
  
  public void recalculateNow() {
    alreadyDone = false;
    getInternalAuthCredentials();
  }
  
}

