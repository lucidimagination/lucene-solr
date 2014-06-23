package org.apache.solr.servlet.security;

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
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegExpAuthorizationFilter implements Filter {
  
  final Logger log = LoggerFactory.getLogger(RegExpAuthorizationFilter.class);

  public static class RegExpPatternAndRoles {
    public int order;
    public String name;
    public Pattern regExpPattern;
    public String[] roles;
  }
  private List<RegExpPatternAndRoles> authorizationConstraints;
  
  @Override
  public void destroy() {}

  @Override
  public void init(FilterConfig config) throws ServletException {
    List<String> constraints = new ArrayList<>();
    Enumeration<String> initParamNames = config.getInitParameterNames();
    while (initParamNames.hasMoreElements()) {
      String initParamName = initParamNames.nextElement();
      String initParamValue = config.getInitParameter(initParamName);
      constraints.add(initParamName);
      constraints.add(initParamValue);
    }
    authorizationConstraints = getOrderedAuthorizationConstraints(constraints.toArray(new String[]{}), log);
  }
  
  public static List<RegExpPatternAndRoles> getOrderedAuthorizationConstraints(String[] constraints, Logger log) {
    ArrayList<RegExpPatternAndRoles> authorizationConstraints = new ArrayList<>();
    for (int i = 0; i < constraints.length; i+=2) {
      String name = constraints[i];
      String value = constraints[i+1];
      int orderRolesSplit = value.indexOf("|");
      int rolesPatternSplit = value.indexOf("|", orderRolesSplit+1);
      // Do not use StringUtils.split here because pattern might contain | (roles are not allowed to for this filter to work)
      String[] orderRolesAndPattern = new String[]{value.substring(0, orderRolesSplit), value.substring(orderRolesSplit+1, rolesPatternSplit), value.substring(rolesPatternSplit+1)};
      RegExpPatternAndRoles regExpPatternAndRoles = new RegExpPatternAndRoles();
      regExpPatternAndRoles.order = Integer.parseInt(orderRolesAndPattern[0].trim());
      regExpPatternAndRoles.name = name;
      regExpPatternAndRoles.roles = StringUtils.split(orderRolesAndPattern[1], ","); 
      String regExp = orderRolesAndPattern[2].trim();
      regExpPatternAndRoles.regExpPattern = Pattern.compile(regExp);
      authorizationConstraints.add(regExpPatternAndRoles);
      if (log != null) {
        log.debug("Added authorization constraint - order: " + regExpPatternAndRoles.order + ", name: " + name + ", reg-exp: " + regExp + ", authorized-roles: " + printRoles(regExpPatternAndRoles.roles));
      }
    }
    Collections.sort(authorizationConstraints, new Comparator<RegExpPatternAndRoles>() {
      @Override
      public int compare(RegExpPatternAndRoles a, RegExpPatternAndRoles b) {
        return a.order - b.order;
      }
    });
    return authorizationConstraints;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp,
      FilterChain chain) throws IOException, ServletException {
    if (req instanceof HttpServletRequest) {
      HttpServletRequest httpReq = (HttpServletRequest)req;
      outherLoop: for (RegExpPatternAndRoles regExpPatternAndRoles : authorizationConstraints) {
        // httpReq.getServletPath() seems to work on a "real" jetty in production setup - httpReq.getPathInfo() doesnt
        String servletPath = httpReq.getServletPath();
        // httpReq.getPathInfo() seems to work on a "test" jetty set up by JettySolrRunner - httpReq.getServletPath() doesnt
        if (StringUtils.isEmpty(servletPath)) servletPath = httpReq.getPathInfo();
        log.debug("Pattern matching on servlet-path: " + servletPath);
        Matcher matcher = regExpPatternAndRoles.regExpPattern.matcher(servletPath);
        if (matcher.find()) {
          for (int i = 0; i < regExpPatternAndRoles.roles.length; i++) {
            if (httpReq.isUserInRole(regExpPatternAndRoles.roles[i])) {
              break outherLoop;
            }
          }
          // It is by intent that we do not check the remaining constraints as soon as we have had a match on the URL-pattern but not on authorization
          // because that is the way security-constraints in deployment descriptors (web.xml) works, and guess its nice to have similar behavior
          Principal principal = httpReq.getUserPrincipal();
          log.warn(((principal != null)?principal.getName():"Unauthenticated user") + " tried to access unauthorized resource " + httpReq.getPathInfo());
          ((HttpServletResponse)resp).sendError(403, "Unauthorized");
          return;
        }
      }
    }
    
    chain.doFilter(req, resp);
  }
  
  private static String printRoles(String[] roles) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < roles.length; i++) {
      if (i > 0) sb.append(" ");
      sb.append(roles[i]);
    }
    return sb.toString();
  }

}


