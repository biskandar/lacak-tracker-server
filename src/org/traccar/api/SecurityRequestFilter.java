/*
 * Copyright 2015 - 2016 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.api;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.xml.bind.DatatypeConverter;

import org.traccar.Context;
import org.traccar.api.resource.SessionResource;
import org.traccar.model.User;

public class SecurityRequestFilter implements ContainerRequestFilter {
  
  public static final String AUTHORIZATION_HEADER = "Authorization";
  public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
  public static final String BASIC_REALM = "Basic realm=\"api\"";
  
  public static String[] decodeBasicAuth(String auth) {
    auth = auth.replaceFirst("[B|b]asic ", "");
    byte[] decodedBytes = DatatypeConverter.parseBase64Binary(auth);
    if (decodedBytes != null && decodedBytes.length > 0) {
      return new String(decodedBytes, StandardCharsets.US_ASCII).split(":", 2);
    }
    return null;
  }
  
  @javax.ws.rs.core.Context
  private HttpServletRequest request;
  
  @javax.ws.rs.core.Context
  private ResourceInfo resourceInfo;
  
  @Override
  public void filter(ContainerRequestContext requestContext) {
    
    if (requestContext.getMethod().equals("OPTIONS")) {
      return;
    }
    
    SecurityContext securityContext = null;
    
    String authHeader = requestContext.getHeaderString(AUTHORIZATION_HEADER);
    if (authHeader != null) {
      
      try {
        String[] auth = decodeBasicAuth(authHeader);
        User user = Context.getDataManager().login(auth[0], auth[1]);
        if (user != null) {
          securityContext = new UserSecurityContext(new UserPrincipal(
              user.getId()));
        }
      } catch (SQLException e) {
        throw new WebApplicationException(e);
      }
      
    } else if (request.getSession() != null) {
      
      Long userId = (Long) request.getSession().getAttribute(
          SessionResource.USER_ID_KEY);
      if (userId != null) {
        securityContext = new UserSecurityContext(new UserPrincipal(userId));
      }
      
    }
    
    if (securityContext != null) {
      requestContext.setSecurityContext(securityContext);
    } else {
      Method method = resourceInfo.getResourceMethod();
      if (!method.isAnnotationPresent(PermitAll.class)) {
        throw new WebApplicationException(Response
            .status(Response.Status.UNAUTHORIZED)
            .header(WWW_AUTHENTICATE, BASIC_REALM).build());
      }
    }
    
  }
  
}
