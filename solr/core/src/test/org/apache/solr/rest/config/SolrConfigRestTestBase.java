package org.apache.solr.rest.config;

import java.io.File;
import java.io.IOException;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.solr.util.RestTestBase;
import org.junit.Before;
import org.junit.BeforeClass;
import org.restlet.ext.servlet.ServerServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jetty.servlet.ServletHolder;

public class SolrConfigRestTestBase extends RestTestBase {

  private static File tmpSolrHome;
  private static File tmpConfDir;

  private static final String collection = "collection1";
  private static final String confDir = collection + "/conf";

  static Logger log = LoggerFactory.getLogger(SolrConfigRestTestBase.class);

  protected static File main;

  @BeforeClass
  public static void init() throws Exception {
    createTempDir();
    tmpSolrHome = new File(TEMP_DIR + File.separator + "config-junit");
    tmpConfDir = new File(tmpSolrHome, confDir);

    main = new File(getFile("solr-config/collection1/conf").getAbsolutePath());
    
    File solrMain = new File(getFile("solr").getAbsolutePath());

    FileUtils.copyDirectory(solrMain, tmpSolrHome);
    FileUtils.copyDirectory(main, tmpConfDir);

    final SortedMap<ServletHolder, String> extraServlets = new TreeMap<ServletHolder, String>();
    final ServletHolder solrRestApi = new ServletHolder("SolrConfigRestApi",
            ServerServlet.class);
    solrRestApi.setInitParameter("org.restlet.application",
            "org.apache.solr.rest.SolrConfigRestApi");
    extraServlets.put(solrRestApi, "/config/*");
        
    createJettyAndHarness(tmpSolrHome.getAbsolutePath(), "solrconfig.xml",
            "schema.xml", "/solr", true, extraServlets);
  }
  
  @Before
  public void restoreSolrConfig() {

    File ori = new File(tmpConfDir, "solrconfig.xml");
    File backup = new File(main, "solrconfig.xml");
    
    log.info("Original file = " + ori.getAbsolutePath());
    log.info("Backup file = " + backup.getAbsolutePath());

    try {
      if (ori.delete()) {
        log.info("solrconfig.xml deleted successfully.");
      } else {
        log.info("solrconfig.xml couldn't be deleted.");
      }

      FileUtils.copyFile(backup, ori);
      log.info("Backup file recovered successfully.");
    } catch (IOException e) {
      log.error("Error: ", e);
    }
  }
  
  public static void assertNotExists(String request, String... tests)
          throws Exception {
    try {
      assertJQ(request, tests);
    } catch (Exception e) {
      if (!e.getMessage().contains("Path not found:"))
        throw e;
    }
  }
}
