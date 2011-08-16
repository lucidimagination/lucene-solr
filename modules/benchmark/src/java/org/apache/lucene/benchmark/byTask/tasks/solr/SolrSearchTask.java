package org.apache.lucene.benchmark.byTask.tasks.solr;

/**
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

import java.io.PrintStream;

import org.apache.lucene.benchmark.byTask.PerfRunData;
import org.apache.lucene.benchmark.byTask.feeds.QueryMaker;
import org.apache.lucene.benchmark.byTask.tasks.PerfTask;
import org.apache.lucene.search.Query;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;

public class SolrSearchTask extends PerfTask {
  
  private final QueryMaker queryMaker;
  
  public SolrSearchTask(PerfRunData runData) {
    super(runData);
    
    queryMaker = getRunData().getQueryMaker(this);
    
  }
  
  @Override
  protected String getLogMessage(int recsCount) {
    return recsCount + " queries sent";
  }
  
  @Override
  public int doLogic() throws Exception {
    
    SolrServer solrServer = getRunData().getSolrServer();
    Query q = queryMaker.makeQuery();
    // nocommit - cannot use toString
    try {
      solrServer.query(new SolrQuery(q.toString()));
    } catch (Exception e) {
      // query failed
      e.printStackTrace(new PrintStream(System.out));
    }
    
    return 1;
  }
}
