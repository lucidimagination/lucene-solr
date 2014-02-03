package org.apache.solr.rest.config.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.solr.cloud.ZkController;
import org.apache.solr.cloud.ZkSolrResourceLoader;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.rest.config.exception.SolrConfigException;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.BadVersionException;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class CoreApi {

  private static final Logger log = LoggerFactory.getLogger(CoreApi.class);

  public static void uploadSolrConfig(SolrCore solrCore, byte[] data)
          throws BadVersionException, InterruptedException, KeeperException {
    ZkSolrResourceLoader zkLoader = (ZkSolrResourceLoader) solrCore
            .getResourceLoader();
    ZkController zkController = zkLoader.getZkController();
    SolrZkClient zkClient = zkController.getZkClient();
    String solrConfigPath = zkLoader.getCollectionZkPath() + "/"
            + "solrconfig.xml";

    try {
      int schemaZkVersion = zkClient.exists(solrConfigPath, null, true)
              .getVersion();
      Stat stat = zkClient.setData(solrConfigPath, data, schemaZkVersion, true);
      schemaZkVersion = stat.getVersion();
      log.info("Persisted solrconfig.xml file at " + solrConfigPath);
    } catch (KeeperException.BadVersionException e) {
      log.info("Failed to persist solrconfig.xml at " + e.getMessage()
              + " - version mismatch");
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // Restore the interrupted status
      throw e;
    } catch (KeeperException e) {
      throw e;
    }
  }

  private static void recoverSolrStandardMode(CoreContainer coreContainer,
          SolrCore solrCore, File backup) throws SolrConfigException {
    File currentSolrConfig = new File(solrCore.getResourceLoader()
            .getConfigDir() + File.separator + solrCore.getConfigResource());
    File failedSolrConfig = new File(solrCore.getResourceLoader()
            .getConfigDir()
            + File.separator
            + solrCore.getConfigResource()
            + ".failed");

    try {

      if (currentSolrConfig.renameTo(failedSolrConfig)) {
        log.info("Rename failed file, successful operation.");
      } else {
        throw new SolrConfigException("Rename failed file, failed operation.");
      }

      FileUtils.moveFile(backup, currentSolrConfig);

      if (failedSolrConfig.delete()) {
        log.info("Delete failed file, successful operation.");
      } else {
        throw new SolrConfigException("Delete failed file, failed operation.");
      }

    } catch (IOException e1) {
      throw new SolrConfigException(e1.getMessage());
    }

    try {
      coreContainer.reload(solrCore.getName());
    } catch (Throwable t) {
      throw new SolrConfigException(
              "Error recovering backup, solrconfig.xml corrupt.");
    }
  }

  public static void reload(CoreContainer coreContainer, SolrCore solrCore,
          boolean isSolrCloud, File backup)
          throws SolrConfigException, BadVersionException, IOException,
          InterruptedException, KeeperException {

    try {

      if (!isSolrCloud)
        standaloneReload(coreContainer, solrCore, backup);
      else {
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.add("action", "RELOAD");
        params.add("name", solrCore.getCoreDescriptor().getCollectionName());
        
        SolrQueryRequestBase req = new SolrQueryRequestBase(null, params) {
        };

        SolrQueryResponse rsp = new SolrQueryResponse();
        coreContainer.getCollectionsHandler()
                .handleRequestBody(req, rsp);
      }
    } catch (Throwable e) {
      log.error("Error performing reload", e);
    }

    log.info("Successful core reload");
  }

  private static void standaloneReload(CoreContainer coreContainer,
          SolrCore solrCore, File backup) throws SolrConfigException {
    try {
      coreContainer.reload(solrCore.getName());
    } catch (Throwable t) {
      log.info("Error performing reload, Starting recover backup");
      recoverSolrStandardMode(coreContainer, solrCore, backup);
    }

    log.info("Successful core reload");
  }
  
  public static boolean validateCoreReload(Document document, SolrCore core) {

    log.info("Starting testReloadSuccess");

    try {
      
      String name = "collectionTmp_" + System.currentTimeMillis();
      
      File home = new File(FileUtils.getTempDirectory(), "reload");
      File coll = new File(home, name);
      File conf = new File(coll, "conf");
      File solrConfig = new File(conf, "solrconfig.xml");

      FileUtils.writeStringToFile(new File(home, "solr.xml"), "<solr></solr>",
              "UTF-8");
      CoreDescriptor desc = core.getCoreDescriptor();
      FileUtils.copyDirectory(new File(desc.getInstanceDir(), "conf"), conf);

      boolean deleted = solrConfig.delete();

      if (deleted) {
        XMLUtils.writeXml(document, solrConfig);
      } else {
        log.error("original solrconfig.xml couldn't be deleted.");
        return false;
      }

      FileUtils.writeStringToFile(new File(coll, "core.properties"),
              "name="+name, "UTF-8");
      
      String zkHost = System.getProperty("zkHost");
      String zkRun = System.getProperty("zkRun");
      
      System.clearProperty("zkHost");
      System.clearProperty("zkRun");
      
      boolean result = performReloading(home);
      
      if(zkHost != null)
        System.setProperty("zkHost", zkHost);
      if(zkRun != null)
        System.setProperty("zkRun", zkRun);
      
      FileUtils.cleanDirectory(home);
      home.delete();

      return result;
    } catch (Exception e) {
      log.warn("Error setup in try reloading", e);
    }
    return false;
  }

  private static boolean performReloading(File home) {
    CoreContainer cc = null;
    try {
      cc = CoreContainer.createAndLoad(home.getAbsolutePath(), new File(home,
              "solr.xml"));
      if (cc.getCoreInitFailures().size() > 0) {
        for (Exception ex : cc.getCoreInitFailures().values()) {
          log.warn("Error when attempting to reload core: " + ex.getMessage());
        }
        return false;
      }

      log.info("Ending testReloadSuccess");

      return true;
    } finally {
      if (cc != null) {
        cc.shutdown();
      }
    }
  }
}
