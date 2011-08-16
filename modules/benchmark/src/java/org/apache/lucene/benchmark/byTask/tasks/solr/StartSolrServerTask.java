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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.lucene.benchmark.byTask.PerfRunData;
import org.apache.lucene.benchmark.byTask.tasks.PerfTask;
import org.apache.lucene.benchmark.byTask.utils.StreamEater;
import org.apache.lucene.benchmark.byTask.utils.StreamUtils;

public class StartSolrServerTask extends PerfTask {
  
  private static boolean vflag = false;
  private String xmx;
  private boolean log;
  
  public StartSolrServerTask(PerfRunData runData) {
    super(runData);
  }
  
  @Override
  public void setup() throws Exception {
    super.setup();
    this.xmx = getRunData().getConfig().get("solr.internal.server.xmx", "512M");
  }
  
  @Override
  public void tearDown() throws Exception {

  }
  
  @Override
  protected String getLogMessage(int recsCount) {
    return "started solr server";
  }
  
  @Override
  public int doLogic() throws Exception {
    startSolrExample(xmx, log);
    return 1;
  }
  
  private static void startSolrExample(String xmx, boolean log) throws IOException,
      InterruptedException, TimeoutException {

    List<String> cmd = new ArrayList<String>();
    cmd.add("java");
    cmd.add("-Xmx" + xmx);
    cmd.add("-Dsolr.solr.home=solr");
    cmd.add("-Dsolr.data.dir=solr/data");
    cmd.add("-Djetty.port=8983");
    cmd.add("-DSTOP.PORT=8984");
    cmd.add("-DSTOP.KEY=stop.solr");
    cmd.add("-jar");
    cmd.add("start.jar");
    runCmd(cmd, "../../solr/example", log);
    
    // TODO: wait till solr is runnin'
    URL u = new URL("http://127.0.0.1:8983/solr/collection1/select/?q=ping");
    InputStream is = null;
    boolean connected = false;
    HttpURLConnection conn = null;
    long timeout = System.currentTimeMillis() + 10000;
    do {
      
      if (System.currentTimeMillis() > timeout) {
        System.out.println("Could not start Solr - timeout waiting for startup");
        throw new TimeoutException("Solr server did not start in time");
      }
      
      conn = (HttpURLConnection) u.openConnection();
      
      conn.setDoOutput(true);
      conn.setRequestMethod("GET");
      
      try {
        is = conn.getInputStream();
        connected = true;
      } catch (IOException e) {
        is = conn.getErrorStream();
        String response = StreamUtils.toString(is);
        // System.out.println("resp:" + conn.getResponseCode());

        Thread.sleep(1000);
      }
      
    } while (!connected);
    
    String response = StreamUtils.toString(is);
    

  }
  
  @Override
  public void setParams(String params) {
    super.setParams(params);
    System.out.println("params:" + params);
    if (params.equalsIgnoreCase("log")) {
      this.log = true;
      System.out.println("------------> logging to stdout with new SolrServer");
    }
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
  
  private static void runCmd(List<String> cmds, String workingDir, boolean log) throws IOException,
      InterruptedException {
    
    if (vflag) System.out.println(cmds);
    
    ProcessBuilder pb = new ProcessBuilder(cmds);
    if (workingDir != null) {
      pb.directory(new File(workingDir));
    }

    Process p = pb.start();
    
    OutputStream stdout = null;
    OutputStream stderr = null;
    
    if (log) {
      stdout = System.out;
      stderr = System.err;
    }
    
    StreamEater se = new StreamEater(p.getInputStream(), stdout);
    se.start();
    StreamEater se2 = new StreamEater(p.getErrorStream(), stderr);
    se2.start();
  }
}
