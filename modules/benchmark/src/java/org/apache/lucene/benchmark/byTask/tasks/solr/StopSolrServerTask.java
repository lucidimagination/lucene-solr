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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.lucene.benchmark.byTask.PerfRunData;
import org.apache.lucene.benchmark.byTask.tasks.PerfTask;
import org.apache.lucene.benchmark.byTask.utils.StreamEater;
import org.apache.lucene.benchmark.byTask.utils.StreamUtils;

public class StopSolrServerTask extends PerfTask {
  
  private static boolean vflag = false;
  
  public StopSolrServerTask(PerfRunData runData) {
    super(runData);
  }
  
  @Override
  public void setup() throws Exception {
    super.setup();
    
  }
  
  @Override
  public void tearDown() throws Exception {

  }
  
  @Override
  protected String getLogMessage(int recsCount) {
    return "stopping solr server";
  }
  
  @Override
  public int doLogic() throws Exception {
    stopSolrExample();
    return 1;
  }
  
  private static void stopSolrExample() throws IOException,
      InterruptedException, TimeoutException {
    
    List<String> cmd = new ArrayList<String>();
    cmd.add("java");
    cmd.add("-DSTOP.PORT=8984");
    cmd.add("-DSTOP.KEY=stop.solr");
    cmd.add("-jar");
    cmd.add("start.jar");
    cmd.add("--stop");
    runCmd(cmd, "../../solr/example");
    
    // TODO: wait till solr is not runnin'
    URL u = new URL("http://127.0.0.1:8983/solr/collection1/select/?q=are_you_awake_solr");
    InputStream is = null;
    boolean running = false;
    HttpURLConnection conn = null;
    
    long timeout = System.currentTimeMillis() + 10000;
    
    do {
      
      if (System.currentTimeMillis() > timeout) {
        System.out.println("Could not stop Solr - timeout waiting for shutdown");
        throw new TimeoutException("Solr server did not stop in time");
      }
      
      conn = (HttpURLConnection) u.openConnection();
      
      conn.setDoOutput(true);
      conn.setRequestMethod("GET");
      
      try {
        is = conn.getInputStream();
        running = true;
        runCmd(cmd, "../../solr/example");
      } catch (IOException e) {
        running = false;
        is = conn.getErrorStream();
        String response = StreamUtils.toString(is);
        conn.disconnect();
        Thread.sleep(1000);
      }
      
    } while (running);
    
    String response = StreamUtils.toString(is);
    
    conn.disconnect();
  }
  
  private static void runCmd(List<String> cmds, String workingDir)
      throws IOException, InterruptedException {
    
    if (vflag) System.out.println(cmds);
    
    ProcessBuilder pb = new ProcessBuilder(cmds);
    if (workingDir != null) {
      pb.directory(new File(workingDir));
    }

    Process p = pb.start();
    
    StreamEater se = new StreamEater(p.getInputStream(), null);
    se.start();
    StreamEater se2 = new StreamEater(p.getErrorStream(), null);
    se2.start();
  }
}
