package org.apache.solr.cloud;

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

import java.util.Stack;

import org.apache.solr.security.InterSolrNodeAuthCredentialsFactory;
import org.apache.solr.security.SystemPropertiesAuthCredentialsInternalRequestFactory;

public class InterSolrNodeAuthCredentialsFactoryTestingHelper extends InterSolrNodeAuthCredentialsFactory {
  
  private static Stack<SubRequestFactoryWrapper> subRequestFactoryWrapperStack = new Stack<>();
  
  public static abstract class SubRequestFactoryWrapper implements SubRequestFactory {
    
    private SubRequestFactory wrappedSubRequestFactory;
    protected SubRequestFactory getWrappedSubRequestFactory() {
      return wrappedSubRequestFactory;
    }
    
  };

  public static void pushSubRequestFactoryWrapper(SubRequestFactoryWrapper subRequestFactoryWrapper) {
    subRequestFactoryWrapper.wrappedSubRequestFactory = InterSolrNodeAuthCredentialsFactory.getCurrentSubRequestFactory();
    InterSolrNodeAuthCredentialsFactory.setCurrentSubRequestFactory(subRequestFactoryWrapper);
    subRequestFactoryWrapperStack.push(subRequestFactoryWrapper);
  }
  
  public static void popSubRequestFactoryWrapper() {
    SubRequestFactoryWrapper subRequestFactoryWrapper = subRequestFactoryWrapperStack.pop();
    InterSolrNodeAuthCredentialsFactory.setCurrentSubRequestFactory(subRequestFactoryWrapper.wrappedSubRequestFactory);
  }

  private static Stack<InternalRequestFactoryWrapper> internalRequestFactoryWrapperStack = new Stack<>();
  
  public static abstract class InternalRequestFactoryWrapper implements InternalRequestFactory {
    
    private InternalRequestFactory wrappedInternalRequestFactory;
    protected InternalRequestFactory getWrappedInternalRequestFactory() {
      return wrappedInternalRequestFactory;
    }
    
  };

  public static void pushInternalRequestFactoryWrapper(InternalRequestFactoryWrapper internalRequestFactoryWrapper) {
    internalRequestFactoryWrapper.wrappedInternalRequestFactory = InterSolrNodeAuthCredentialsFactory.getCurrentInternalRequestFactory();
    InterSolrNodeAuthCredentialsFactory.setCurrentInternalRequestFactory(internalRequestFactoryWrapper);
    internalRequestFactoryWrapperStack.push(internalRequestFactoryWrapper);
  }
  
  public static void popInternalRequestFactoryWrapper() {
    InternalRequestFactoryWrapper internalRequestFactoryWrapper = internalRequestFactoryWrapperStack.pop();
    InterSolrNodeAuthCredentialsFactory.setCurrentInternalRequestFactory(internalRequestFactoryWrapper.wrappedInternalRequestFactory);
  }
  
  public static void recalculateNowOnDefaultInternalRequestFactory() {
    recalculateNowOnDefaultInternalRequestFactory(InterSolrNodeAuthCredentialsFactory.getCurrentInternalRequestFactory());
  }
  
  private static void recalculateNowOnDefaultInternalRequestFactory(InternalRequestFactory internalRequestFactory) {
    if (internalRequestFactory instanceof SystemPropertiesAuthCredentialsInternalRequestFactory) {
      ((SystemPropertiesAuthCredentialsInternalRequestFactory)internalRequestFactory).recalculateNow();
    } else if (internalRequestFactory instanceof InternalRequestFactoryWrapper) {
      recalculateNowOnDefaultInternalRequestFactory(((InternalRequestFactoryWrapper)internalRequestFactory).wrappedInternalRequestFactory);
    }
  }

}


