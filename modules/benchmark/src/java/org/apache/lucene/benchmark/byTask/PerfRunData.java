package org.apache.lucene.benchmark.byTask;

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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Locale;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.benchmark.byTask.feeds.DocMaker;
import org.apache.lucene.benchmark.byTask.feeds.QueryMaker;
import org.apache.lucene.benchmark.byTask.stats.Points;
import org.apache.lucene.benchmark.byTask.tasks.NewAnalyzerTask;
import org.apache.lucene.benchmark.byTask.tasks.PerfTask;
import org.apache.lucene.benchmark.byTask.tasks.SearchTask;
import org.apache.lucene.benchmark.byTask.utils.Config;
import org.apache.lucene.benchmark.byTask.utils.FileUtils;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.impl.StreamingUpdateSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;

/**
 * Data maintained by a performance test run.
 * <p>
 * Data includes:
 * <ul>
 *  <li>Configuration.
 *  <li>Directory, Writer, Reader.
 *  <li>Docmaker and a few instances of QueryMaker.
 *  <li>Analyzer.
 *  <li>Statistics data which updated during the run.
 * </ul>
 * Config properties: work.dir=&lt;path to root of docs and index dirs| Default: work&gt;
 * </ul>
 */
public class PerfRunData {

  private Points points;
  
  // objects used during performance test run
  // directory, analyzer, docMaker - created at startup.
  // reader, writer, searcher - maintained by basic tasks. 
  private Directory directory;
  private Analyzer analyzer;
  private SolrServer solrServer;
  private DocMaker docMaker;
  private Locale locale;
  
  // we use separate (identical) instances for each "read" task type, so each can iterate the quries separately.
  private HashMap<Class<? extends PerfTask>,QueryMaker> readTaskQueryMaker;
  private Class<? extends QueryMaker> qmkrClass;

  private IndexReader indexReader;
  private IndexSearcher indexSearcher;
  private IndexWriter indexWriter;
  private Config config;
  private long startTimeMillis;
  
  // constructor
  public PerfRunData (Config config) throws Exception {
    this.config = config;
    // analyzer (default is standard analyzer)
    String analyzerClass = config.get("analyzer", "org.apache.lucene.analysis.standard.StandardAnalyzer");
    if (analyzerClass != null) {
      analyzer = NewAnalyzerTask.createAnalyzer(analyzerClass);
    }

    initSolrStuff();
    
    // doc maker
    docMaker = Class.forName(config.get("doc.maker",
        "org.apache.lucene.benchmark.byTask.feeds.DocMaker")).asSubclass(DocMaker.class).newInstance();
    docMaker.setConfig(config);
    // query makers
    readTaskQueryMaker = new HashMap<Class<? extends PerfTask>,QueryMaker>();
    qmkrClass = Class.forName(config.get("query.maker","org.apache.lucene.benchmark.byTask.feeds.SimpleQueryMaker")).asSubclass(QueryMaker.class);

    // index stuff
    reinit(false);
    
    // statistic points
    points = new Points(config);
    
    if (Boolean.valueOf(config.get("log.queries","false")).booleanValue()) {
      System.out.println("------------> queries:");
      System.out.println(getQueryMaker(new SearchTask(this)).printQueries());
    }
  }

  private void initSolrStuff() throws Exception {
    String solrServerUrl = config.get("solr.url", null);
    boolean internalSolrServer = false;
    if (solrServerUrl == null) {
      internalSolrServer = true;
      solrServerUrl = "http://localhost:8983/solr";
    }
    

    
    String solrCollection = config.get("solr.collection", "collection1");
    String solrServerClass = config.get("solr.server", null);
    
    if (!internalSolrServer) {
      if (solrServerUrl == null || solrServerClass == null) {
        throw new RuntimeException("You must set solr.url and solr.server to run a remote benchmark algorithm");
      }
    }
    
    if (solrServerClass != null) {
      System.out.println("------------> new SolrServer with URL:" + solrServerUrl + "/" + solrCollection);
      Class<?> clazz = this.getClass().getClassLoader()
          .loadClass(solrServerClass);
      
      Constructor[] cons = clazz.getConstructors();
      for (Constructor con : cons) {
        Class[] types = con.getParameterTypes();
        if (types.length == 1 && types[0] == String.class) {
          solrServer = (SolrServer) con.newInstance(solrServerUrl + "/" + solrCollection);
        } else if (types.length == 3
            && clazz == StreamingUpdateSolrServer.class) {
          int queueSize = config.get("solr.streaming.server.queue.size", 100);
          int threadCount = config.get("solr.streaming.server.threadcount", 2);
          solrServer = (SolrServer) con.newInstance(solrServerUrl + "/" + solrCollection, queueSize,
              threadCount);
        } 
      }
      if (solrServer == null) {
        throw new RuntimeException("Could not understand solr.server config:"
            + solrServerClass);
        
      }
    }
    
    String configDir = config.get("solr.config.dir", null);
    
    if (configDir == null && internalSolrServer) {
      configDir = "../../solr/example/solr/conf";
    }
    
    String configsHome = config.get("solr.configs.home", null);
   
    if (configDir != null && configsHome != null) {
      System.out.println("------------> solr.configs.home: " + new File(configsHome).getAbsolutePath());
      String solrConfig = config.get("solr.config", null);
      String schema = config.get("solr.schema", null);
      
      boolean copied = false;
      
      if (solrConfig != null) {
        File solrConfigFile = new File(configsHome, solrConfig);
        if (solrConfigFile.exists() && solrConfigFile.canRead()) {
          copy(solrConfigFile, new File(configDir, "solrconfig.xml"));
          copied = true;
        } else {
          throw new RuntimeException("Could not find or read:" + solrConfigFile);
        }
      }
      if (schema != null) {

        File schemaFile = new File(configsHome, schema);
        if (schemaFile.exists() && schemaFile.canRead()) {
          System.out.println("------------> using schema: " + schema);
          copy(schemaFile, new File(configDir, "schema.xml"));
          copied = true;
        } else {
          throw new RuntimeException("Could not find or read:" + schemaFile);
        }
      }
      
      if (copied && solrServer != null && !internalSolrServer) {
        // nocommit: hard coded collection1 and check response
        CoreAdminResponse result = CoreAdminRequest.reloadCore(solrCollection, new CommonsHttpSolrServer(solrServerUrl));
      }
    }
   
  }
  
  // closes streams for you
  private static void copy(File in, File out) throws IOException {
    System.out.println("------------> copying: " + in + " to " + out);
    FileOutputStream os = new FileOutputStream(out);
    FileInputStream is = new FileInputStream(in);
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        writer.write(line + System.getProperty("line.separator"));
      }
    } finally {
      reader.close();
      writer.close();
    }
  }

  // clean old stuff, reopen 
  public void reinit(boolean eraseIndex) throws Exception {

    // cleanup index
    if (indexWriter!=null) {
      indexWriter.close();
      indexWriter = null;
    }
    if (indexReader!=null) {
      indexReader.close();
      indexReader = null;
    }
    if (directory!=null) {
      directory.close();
    }
    
    // directory (default is ram-dir).
    if ("FSDirectory".equals(config.get("directory","RAMDirectory"))) {
      File workDir = new File(config.get("work.dir","work"));
      File indexDir = new File(workDir,"index");
      if (eraseIndex && indexDir.exists()) {
        FileUtils.fullyDelete(indexDir);
      }
      indexDir.mkdirs();
      directory = FSDirectory.open(indexDir);
    } else {
      directory = new RAMDirectory();
    }

    // inputs
    resetInputs();
    
    initSolrStuff();
    
    // release unused stuff
    System.runFinalization();
    System.gc();

    // Re-init clock
    setStartTimeMillis();
  }
  
  public long setStartTimeMillis() {
    startTimeMillis = System.currentTimeMillis();
    return startTimeMillis;
  }

  /**
   * @return Start time in milliseconds
   */
  public long getStartTimeMillis() {
    return startTimeMillis;
  }

  /**
   * @return Returns the points.
   */
  public Points getPoints() {
    return points;
  }

  /**
   * @return Returns the directory.
   */
  public Directory getDirectory() {
    return directory;
  }

  /**
   * @param directory The directory to set.
   */
  public void setDirectory(Directory directory) {
    this.directory = directory;
  }

  /**
   * @return Returns the indexReader.  NOTE: this returns a
   * reference.  You must call IndexReader.decRef() when
   * you're done.
   */
  public synchronized IndexReader getIndexReader() {
    if (indexReader != null) {
      indexReader.incRef();
    }
    return indexReader;
  }

  /**
   * @return Returns the indexSearcher.  NOTE: this returns
   * a reference to the underlying IndexReader.  You must
   * call IndexReader.decRef() when you're done.
   */
  public synchronized IndexSearcher getIndexSearcher() {
    if (indexReader != null) {
      indexReader.incRef();
    }
    return indexSearcher;
  }

  /**
   * @param indexReader The indexReader to set.
   */
  public synchronized void setIndexReader(IndexReader indexReader) throws IOException {
    if (this.indexReader != null) {
      // Release current IR
      this.indexReader.decRef();
    }
    this.indexReader = indexReader;
    if (indexReader != null) {
      // Hold reference to new IR
      indexReader.incRef();
      indexSearcher = new IndexSearcher(indexReader);
    } else {
      indexSearcher = null;
    }
  }

  /**
   * @return Returns the indexWriter.
   */
  public IndexWriter getIndexWriter() {
    return indexWriter;
  }

  /**
   * @param indexWriter The indexWriter to set.
   */
  public void setIndexWriter(IndexWriter indexWriter) {
    this.indexWriter = indexWriter;
  }

  /**
   * @return Returns the anlyzer.
   */
  public Analyzer getAnalyzer() {
    return analyzer;
  }


  public void setAnalyzer(Analyzer analyzer) {
    this.analyzer = analyzer;
  }
  
  public final SolrServer getSolrServer() {
    return solrServer;
  }

  public final void setSolrServer(SolrServer solrServer) {
    this.solrServer = solrServer;
  }

  /** Returns the docMaker. */
  public DocMaker getDocMaker() {
    return docMaker;
  }

  /**
   * @return the locale
   */
  public Locale getLocale() {
    return locale;
  }

  /**
   * @param locale the locale to set
   */
  public void setLocale(Locale locale) {
    this.locale = locale;
  }

  /**
   * @return Returns the config.
   */
  public Config getConfig() {
    return config;
  }

  public void resetInputs() throws IOException {
    docMaker.resetInputs();
    for (final QueryMaker queryMaker : readTaskQueryMaker.values()) {
      queryMaker.resetInputs();
    }
  }

  /**
   * @return Returns the queryMaker by read task type (class)
   */
  synchronized public QueryMaker getQueryMaker(PerfTask task) {
    // mapping the query maker by task class allows extending/adding new search/read tasks
    // without needing to modify this class.
    Class<? extends PerfTask> readTaskClass = task.getClass();
    QueryMaker qm = readTaskQueryMaker.get(readTaskClass);
    if (qm == null) {
      try {
        qm = qmkrClass.newInstance();
        qm.setConfig(config);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      readTaskQueryMaker.put(readTaskClass,qm);
    }
    return qm;
  }

}
