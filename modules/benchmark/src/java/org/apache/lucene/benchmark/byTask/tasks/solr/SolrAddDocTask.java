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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.benchmark.byTask.PerfRunData;
import org.apache.lucene.benchmark.byTask.feeds.DocMaker;
import org.apache.lucene.benchmark.byTask.tasks.PerfTask;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;

/**
 * Add a document, optionally with of a certain size. <br>
 * Other side effects: none. <br>
 * Takes optional param: document size.
 */
public class SolrAddDocTask extends PerfTask {
  
  private volatile Map<String,String> fieldMappings = new HashMap<String,String>();
  
  public SolrAddDocTask(PerfRunData runData) {
    super(runData);
  }
  
  private int docSize = 0;
  
  // volatile data passed between setup(), doLogic(), tearDown().
  private Document doc = null;
  
  private volatile int id = 0;
  
  @Override
  public void setup() throws Exception {
    try {
    super.setup();
    DocMaker docMaker = getRunData().getDocMaker();
    if (docSize > 0) {
      doc = docMaker.makeDocument(docSize);
    } else {
      doc = docMaker.makeDocument();
    }
    
    // get field mappings - TODO: we don't want to every instance...
    
    String solrFieldMappings = getRunData().getConfig().get(
        "solr.field.mappings", null);
    if (solrFieldMappings != null) {
      String[] mappings = solrFieldMappings.split(",");
      Map<String,String> fieldMappings = new HashMap<String,String>(
          mappings.length);
      for (String mapping : mappings) {
        int index = mapping.indexOf(">");
        if (index == -1) {
          System.err.println("Invalid Solr field mapping:" + mapping);
          continue;
        }
        String from = mapping.substring(0, index);
        String to = mapping.substring(index + 1, mapping.length());
        // System.err.println("From:" + from + " to:" + to);
        fieldMappings.put(from, to);
      }
      this.fieldMappings = fieldMappings;
    }
    } catch(Exception e) {
      e.printStackTrace(new PrintStream(System.out));
    }
  }
  
  @Override
  public void tearDown() throws Exception {
    doc = null;
    fieldMappings.clear();
    super.tearDown();
  }
  
  @Override
  protected String getLogMessage(int recsCount) {
    return "added " + recsCount + " docs";
  }
  
  @Override
  public int doLogic() throws Exception {
    
    UpdateResponse resp = null;
    try {
      SolrServer solrServer = getRunData().getSolrServer();
      
      SolrInputDocument solrDoc = new SolrInputDocument();
      List<Fieldable> fields = doc.getFields();
      for (Fieldable field : fields) {
        // System.err.println("field:" + field.name());
        String name = field.name();
        String mappedName = fieldMappings.get(name);
        if (mappedName == null) {
          mappedName = name;
        }
        // System.err.println("mapped field:" + mappedName);
        solrDoc.addField(mappedName, field.stringValue());
      }
      
      if (solrDoc.getField("id") == null) {
        solrDoc.addField("id", id++);
      }
      
      resp = solrServer.add(solrDoc);
    } catch (Throwable e) {
      e.printStackTrace(new PrintStream(System.out));
      throw new RuntimeException(e);
    }

    return 1;
  }
  
  /**
   * Set the params (docSize only)
   * 
   * @param params
   *          docSize, or 0 for no limit.
   */
  @Override
  public void setParams(String params) {
    super.setParams(params);
    docSize = (int) Float.parseFloat(params);
  }
  
  /*
   * (non-Javadoc)
   * 
   * @see org.apache.lucene.benchmark.byTask.tasks.PerfTask#supportsParams()
   */
  @Override
  public boolean supportsParams() {
    return true;
  }
  
}
