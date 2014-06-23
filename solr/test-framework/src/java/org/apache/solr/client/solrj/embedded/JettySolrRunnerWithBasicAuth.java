package org.apache.solr.client.solrj.embedded;

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
import org.apache.solr.servlet.SolrDispatchFilter;
import org.apache.solr.servlet.security.RegExpAuthorizationFilter;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.MappedLoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.security.Password;

import javax.security.auth.Subject;
import javax.servlet.DispatcherType;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

/**
 * Run Solr in Jetty, but with Basic Auth. For test use only.
 * @see JettySolrRunner
 * @since Solr 4.4
 */
public class JettySolrRunnerWithBasicAuth extends JettySolrRunner {
  
  public static final String SEARCH_USERNAME = "search-user";
  public static final String SEARCH_PASSWORD = "search-pass";
  public static final String SEARCH_ROLE = "search-role";
  public static final AuthCredentials SEARCH_CREDENTIALS = AuthCredentials.createBasicAuthCredentials(SEARCH_USERNAME, SEARCH_PASSWORD);

  public static final String UPDATE_USERNAME = "update-user";
  public static final String UPDATE_PASSWORD = "update-pass";
  public static final String UPDATE_ROLE = "update-role";
  public static final AuthCredentials UPDATE_CREDENTIALS = AuthCredentials.createBasicAuthCredentials(UPDATE_USERNAME, UPDATE_PASSWORD);

  public static final String ALL_USERNAME = "all-user";
  public static final String ALL_PASSWORD = "all-pass";
  public static final String ALL_ROLE = "all-role";
  public static final AuthCredentials ALL_CREDENTIALS = AuthCredentials.createBasicAuthCredentials(ALL_USERNAME, ALL_PASSWORD);
  
  public static final AuthCredentials getAuthCredentialsForRole(String role) {
    if (SEARCH_ROLE.equals(role)) {
      return SEARCH_CREDENTIALS;
    } else if (UPDATE_ROLE.equals(role)) {
      return UPDATE_CREDENTIALS;
    } else if (ALL_ROLE.equals(role)) {
      return ALL_CREDENTIALS;
    }
    return null;
  }

  public static final String[] regExpSecurityFilterInitParams = new String[] {
    "update-constraint", "1|" + UPDATE_ROLE + "," + ALL_ROLE + "|^.*/update$",
    "search-constraint", "2|" + SEARCH_ROLE + "," + ALL_ROLE + "|^.*/select$",
    "terms-constraint", "3|" + SEARCH_ROLE + "," + ALL_ROLE + "|^.*/terms$",
    "get-constraint", "4|" + SEARCH_ROLE + "," + ALL_ROLE + "|^.*/get$",
    "all-constraint", "5|" + ALL_ROLE + "|^.*$"
  };

  private FilterHolder regExpSecurityFilterHolder;  

  public JettySolrRunnerWithBasicAuth(String solrHome, String context, int port, String solrConfigFilename, String schemaFileName, boolean stopAtShutdown, SortedMap<ServletHolder, String> extraServlets, SSLConfig sslConfig,
                                      SortedMap<Class, String> extraRequestFilters) {
    super(solrHome, context, port, solrConfigFilename, schemaFileName, stopAtShutdown, extraServlets, sslConfig, extraRequestFilters);
  }

  @Override
  protected void init(String solrHome, String context, int port, boolean stopAtShutdown) {
    super.init(solrHome, context, port, stopAtShutdown);
    ConstraintSecurityHandler security = (ConstraintSecurityHandler)server.getHandler();

    // Setting up web-container handled authentication (and authorization)
    final List<ConstraintMapping> constraintMappings = new ArrayList<>();
    // Configuring web-container authorization. We want to have the following set of constraints
    // -----------------------------------------------------
    // | Authorization             | Roles                 |
    // -----------------------------------------------------
    // | Search any collection     | all-role, search-role |
    // | Index into any collection | all-role, update-role |
    // | Anything else             | all-role              |
    // -----------------------------------------------------
    // We could have configured the web-container to handle all authorization, but that would take a huge amount of constraints to be set up
    // due to limitations on patterns in "url-pattern" in "security-constraint|web-resource-collection"'s in web.xml. You cannot write stuff like
    //   <url-pattern>/solr/*/search</url-pattern>, <url-pattern>*/search</url-pattern> or <url-pattern>*search</url-pattern>
    // Therefore you would need to repeat the following lines for each and every collection the tests create (and that can currently be 40+)
    //   constraintMappings.add(createConstraint("update-<ollection-name>",  "/solr/<collection-name>/update", new String[]{"all-role", "update-role"} ));
    //   constraintMappings.add(createConstraint("search-<ollection-name>",  "/solr/<collection-name>/select", new String[]{"all-role", "search-role"} ));
    //   constraintMappings.add(createConstraint("term-<ollection-name>",  "/solr/<collection-name>/term", new String[]{"all-role", "search-role"} ));
    //   constraintMappings.add(createConstraint("get-<ollection-name>",  "/solr/<collection-name>/get", new String[]{"all-role", "search-role"} ));
    // Basic reason this needs to be done to have the fairly simple authorization constrains described above is that URLs are constructed
    // /solr/<collection-or-replica>/<operation> instead of /solr/<operation>/<collection-or-replica>, making it hard to authorize on "operation but across all collection",
    // but easy to authorize on "collection but all operations"
    // Therefore we basically only configure the web-container to control that all URLs require authentication but authorize to any role, and then program our
    // way out of the authorization stuff by using RegExpAuthorizationFilter filter - see setup below
    //
    // Now setting up what corresponds to the following in web.xml
    // <security-constraint>
    //   <web-resource-collection>
    //     <web-resource-name>All resources need authentication</web-resource-name>
    //     <url-pattern>/*</url-pattern>
    //   </web-resource-collection>
    //   <auth-constraint>
    //     <role-name>*</role-name>
    //   </auth-constraint>
    // </security-constraint>
    constraintMappings.add(createConstraint("All resources need authentication",  "/*", new String[]{"*"} ));

    Set<String> knownRoles = new HashSet<>();
    for (ConstraintMapping constraintMapping : constraintMappings) {
      for (String role : constraintMapping.getConstraint().getRoles()) {
        knownRoles.add(role);
      }
    }

    // Setting up authentication realm
    // -------------------------------------------
    // | Username    | Password    | Roles       |
    // -------------------------------------------
    // | search-user | search-pass | search-role |
    // | update-user | update-pass | update-role |
    // | all-user    | all-pass    | all-role    |
    // -------------------------------------------
    // Now setting up what corresponds to the following in jetty.xml (v8)
    //<Call name="addBean">
    //  <Arg>
    //    <New class="Anonymous class extending MappedLoginService">
    //      <Set name="name">MyRealm</Set>
    //    </New>
    //  </Arg>
    //</Call>
    LoginService loginService = new MappedLoginService() {

      @Override
      protected synchronized UserIdentity putUser(String userName, Object info)
      {
        final UserIdentity identity;
        if (info instanceof UserIdentity)
          identity=(UserIdentity)info;
        else
        {
          Credential credential = (info instanceof Credential)?(Credential)info:Credential.getCredential(info.toString());

          Principal userPrincipal = new KnownUser(userName,credential);
          Subject subject = new Subject();

          identity=_identityService.newUserIdentity(subject,userPrincipal, IdentityService.NO_ROLES);
        }

        _users.put(userName,identity);
        return identity;
      }

      @Override
      public synchronized UserIdentity putUser(String userName, Credential credential, String[] roles)
      {
        Principal userPrincipal = new KnownUser(userName,credential);
        Subject subject = new Subject();

        UserIdentity identity=_identityService.newUserIdentity(subject,userPrincipal,roles);
        _users.put(userName,identity);
        return identity;
      }

      @Override
      protected UserIdentity loadUser(String arg0) {
        return null;
      }

      @Override
      protected void loadUsers() throws IOException {
        // For test purpose just a hard coded set of user/password/roles
        putUser(SEARCH_USERNAME, new Password(SEARCH_PASSWORD), new String[]{SEARCH_ROLE});
        putUser(UPDATE_USERNAME, new Password(UPDATE_PASSWORD), new String[]{UPDATE_ROLE});
        putUser(ALL_USERNAME, new Password(ALL_PASSWORD), new String[]{ALL_ROLE});
      }
    };
    server.addBean(loginService);

    // Now setting up what corresponds to the following in web.xml
    // <login-config>
    //   <auth-method>BASIC</auth-method>
    //   <realm-name>MyRealm</realm-name>
    // </login-config>
    security.setConstraintMappings(constraintMappings, knownRoles);
    security.setAuthenticator(new BasicAuthenticator());
    security.setLoginService(loginService);
    security.setStrict(false);
  }

  @Override
  protected ServletContextHandler generateRoot(Server server, String context) {
    ConstraintSecurityHandler security = new ConstraintSecurityHandler();
    server.setHandler(security);
    return new ServletContextHandler(security,context,ServletContextHandler.SESSIONS);
  }

  @Override
  protected LifeCycle.Listener getLifeCycleListener(final ServletContextHandler root) {
    return new LifeCycle.Listener() {

      @Override
      public void lifeCycleStopping(LifeCycle arg0) {
        System.clearProperty("hostPort");
      }

      @Override
      public void lifeCycleStopped(LifeCycle arg0) {}

      @Override
      public void lifeCycleStarting(LifeCycle arg0) {
        synchronized (JettySolrRunnerWithBasicAuth.this) {
          waitOnSolr = true;
          JettySolrRunnerWithBasicAuth.this.notify();
        }
      }

      @Override
      public void lifeCycleStarted(LifeCycle arg0) {
        lastPort = getFirstConnectorPort();
        System.setProperty("hostPort", Integer.toString(lastPort));
        if (solrConfigFilename != null) System.setProperty("solrconfig",
            solrConfigFilename);
        if (schemaFilename != null) System.setProperty("schema",
            schemaFilename);

        // Setting up filter handled authorization
        // Now setting up what corresponds to the following in web.xml (it is important that this one is the FIRST filter)
        //        <filter>
        //          <filter-name>RegExpAuthorizationFilter</filter-name>
        //          <filter-class>org.apache.solr.servlet.RegExpAuthorizationFilter</filter-class>
        //          <init-param>
        //            <param-name>update-constraint</param-name>
        //            <param-value>1|update-role,all-role|^.*/update$</param-value>
        //          </init-param>
        //          <init-param>
        //            <param-name>search-constraint</param-name>
        //            <param-value>2|search-role,all-role|^.*/select$</param-value>
        //          </init-param>
        //          <init-param>
        //            <param-name>terms-constraint</param-name>
        //            <param-value>3|search-role,all-role|^.*/terms$</param-value>
        //          </init-param>
        //          <init-param>
        //            <param-name>get-constraint</param-name>
        //            <param-value>4|search-role,all-role|^.*/get$</param-value>
        //          </init-param>
        //          <init-param>
        //            <param-name>all-constraint</param-name>
        //            <param-value>5|all-role|^.*$</param-value>
        //          </init-param>
        //        </filter>
        //
        //        <filter-mapping>
        //          <filter-name>RegExpAuthorizationFilter</filter-name>
        //          <url-pattern>/*</url-pattern>
        //        </filter-mapping>
        regExpSecurityFilterHolder = new FilterHolder(new RegExpAuthorizationFilter());
        for (int i = 0; i < regExpSecurityFilterInitParams.length; i+=2) {
          regExpSecurityFilterHolder.setInitParameter(regExpSecurityFilterInitParams[i], regExpSecurityFilterInitParams[i+1]);
        }
        root.addFilter(regExpSecurityFilterHolder, "*", EnumSet.of(DispatcherType.REQUEST));

        debugFilter = root.addFilter(DebugFilter.class, "*", EnumSet.of(DispatcherType.REQUEST) );
        dispatchFilter = root.addFilter(SolrDispatchFilter.class, "*", EnumSet.of(DispatcherType.REQUEST) );
        for (ServletHolder servletHolder : extraServlets.keySet()) {
          String pathSpec = extraServlets.get(servletHolder);
          root.addServlet(servletHolder, pathSpec);
        }
        if (solrConfigFilename != null) System.clearProperty("solrconfig");
        if (schemaFilename != null) System.clearProperty("schema");
        System.clearProperty("solr.solr.home");
      }

      @Override
      public void lifeCycleFailure(LifeCycle arg0, Throwable arg1) {
        System.clearProperty("hostPort");
      }
    };
  }

  private ConstraintMapping createConstraint(String name, String path, String[] roles) {
    Constraint constraint = new Constraint();
    constraint.setName(name);
    constraint.setAuthenticate(true);
    constraint.setRoles(roles);
    ConstraintMapping mapping = new ConstraintMapping();
    mapping.setPathSpec(path);
    mapping.setConstraint(constraint);
    return mapping;
  }

}

