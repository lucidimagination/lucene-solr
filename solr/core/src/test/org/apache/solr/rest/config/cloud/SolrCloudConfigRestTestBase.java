package org.apache.solr.rest.config.cloud;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.solr.JSONTestUtil;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.cloud.AbstractFullDistribZkTestBase;
import org.apache.solr.cloud.AbstractZkTestCase;
import org.apache.solr.rest.config.SolrConfigRestTestBase;
import org.apache.solr.util.RESTfulServerProvider;
import org.apache.solr.util.RestTestHarness;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.restlet.ext.servlet.ServerServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SolrCloudConfigRestTestBase extends
        AbstractFullDistribZkTestBase {

  private static final Logger log = LoggerFactory
          .getLogger(SolrCloudConfigRestTestBase.class);

  private static File BACKUP_SOLR_HOME = null;
  private static File zkDir;
  private static File solrMain;
  private static File solrCloudHome;
  protected List<RestTestHarness> restTestHarnesses = new ArrayList<RestTestHarness>();

  public static final String RELOAD_CORE_REQUEST = "/admin/collections?action=RELOAD&name="
          + DEFAULT_COLLECTION;

  public SolrCloudConfigRestTestBase(int slc, int shc, boolean fsc) {
    super();

    fixShardCount = fsc;
    sliceCount = slc;
    shardCount = shc;
  }

  @BeforeClass
  public static void setupZkFile() throws Exception {
    System.setProperty("managed.schema.mutable", "true");
    System.setProperty("enable.update.log", "true");

    BACKUP_SOLR_HOME = AbstractZkTestCase.SOLRHOME;
    solrCloudHome = new File(getFile("solr-config/collection1/conf")
            .getAbsolutePath());
    zkDir = createTempDir();

    solrMain = new File(getFile("solr").getAbsolutePath());

    FileUtils.copyDirectory(solrMain, zkDir);
    FileUtils.copyDirectory(solrCloudHome, new File(zkDir, "collection1/conf"));

    AbstractZkTestCase.SOLRHOME = zkDir;
  }

  @Override
  protected String getCloudSolrConfig() {
    return "solrconfig.xml";
  }

  @Override
  public SortedMap<ServletHolder, String> getExtraServlets() {
    final SortedMap<ServletHolder, String> extraServlets = new TreeMap<ServletHolder, String>();
    final ServletHolder solrRestApi = new ServletHolder("SolrConfigRestApi",
            ServerServlet.class);
    solrRestApi.setInitParameter("org.restlet.application",
            "org.apache.solr.rest.SolrConfigRestApi");
    extraServlets.put(solrRestApi, "/config/*");
    return extraServlets;
  }

  protected void setupHarnesses() {
    for (int i = 0; i < clients.size(); ++i) {
      final HttpSolrServer client = (HttpSolrServer) clients.get(i);
      RestTestHarness harness = new RestTestHarness(
              new RESTfulServerProvider() {
                @Override
                public String getBaseURL() {
                  return client.getBaseURL();
                }
              });
      restTestHarnesses.add(harness);
    }
  }

  @Override
  protected void setupJettySolrHome(File jettyHome) throws IOException {
    FileUtils.copyDirectory(solrMain, jettyHome);
    FileUtils.copyDirectory(solrCloudHome, new File(jettyHome,
            "collection1/conf"));
  }

  public boolean validate(String response, String... tests) throws Exception {
    boolean failed = true;
    for (String test : tests) {
      if (null == test || 0 == test.length())
        continue;
      String testJSON = json(test);

      String err = "";
      try {
        failed = true;
        err = JSONTestUtil
                .match(response, testJSON, JSONTestUtil.DEFAULT_DELTA);
        failed = false;
        if (err != null) {
          log.error("\n\n\n Error in validate: \n query failed JSON validation. error="
                  + err
                  + "\n expected ="
                  + testJSON
                  + "\n response = "
                  + response);

          return false;
        }
      } finally {
        if (failed) {
          log.error("JSON query validation threw an exception."
                  + "\n expected =" + testJSON + "\n response = " + response);
          throw new RuntimeException(err);
        }
      }
    }
    return true;
  }

  @AfterClass
  public static void restoreSolrHome() {
    AbstractZkTestCase.SOLRHOME = BACKUP_SOLR_HOME;
  }
}
