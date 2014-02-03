package org.apache.solr.rest.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.solr.cloud.ZkController;
import org.apache.solr.cloud.ZkSolrResourceLoader;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.rest.config.exception.SolrConfigException;
import org.apache.solr.rest.config.utils.CoreApi;
import org.apache.solr.rest.config.utils.EditableSolrConfig;
import org.apache.solr.rest.config.utils.ValidateUtils;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.BadVersionException;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

/**
 * 
 * This class is based on org.apache.solr.rest.schema.BaseSchemaResource.java
 * 
 */

public class BaseConfigResource extends ServerResource implements
        CommonServerResource {

  private SolrQueryRequest solrRequest;
  private SolrQueryResponse solrResponse;
  private QueryResponseWriter responseWriter;
  private boolean doIndent;
  private static final Charset UTF8 = Charset.forName("UTF-8");
  private CoreContainer coreContainer;
  private boolean isSolrCloud;
  private ZkController controller;

  protected String contentType;
  protected String coreName;
  protected SolrCore solrCore;
  protected List<Map<String, Object>> errors;

  public BaseConfigResource() {
    errors = new ArrayList<Map<String, Object>>();
  }

  protected Representation errorResponse() {
    getSolrResponse().add("errors", errors);

    return new SolrOutputRepresentation(this, contentType);
  }

  @Override
  public void doInit() throws ResourceException {
    super.doInit();
    setNegotiated(false); // Turn off content negotiation for now
    if (isExisting()) {
      try {
        SolrRequestInfo solrRequestInfo = SolrRequestInfo.getRequestInfo();
        if (null == solrRequestInfo) {
          final String message = "No handler or core found in "
                  + getRequest().getOriginalRef().getPath();
          doError(Status.CLIENT_ERROR_BAD_REQUEST, message);
          setExisting(false);
        } else {
          solrRequest = solrRequestInfo.getReq();
          coreContainer = solrRequest.getCore().getCoreDescriptor()
                  .getCoreContainer();
          if (null == solrRequest) {
            final String message = "No handler or core found in "
                    + getRequest().getOriginalRef().getPath();
            doError(Status.CLIENT_ERROR_BAD_REQUEST, message);
            setExisting(false);
          } else {
            String responseWriterName = solrRequest.getParams().get(CommonParams.WT);
            String indent = getSolrRequest().getParams().get("indent");
            
            if (responseWriterName == null) {
              responseWriterName = "json";
            }

            if (indent != null && ("".equals(indent) || "off".equals(indent))) {
              doIndent = false;
            } else {
              ModifiableSolrParams newParams = new ModifiableSolrParams(solrRequest.getParams());
              newParams.remove(indent);
              newParams.add("indent", "on");
              solrRequest.setParams(newParams);
            }

            solrCore = solrRequest.getCore();
            coreName = solrCore.getName();
            isSolrCloud = (coreContainer.getZkController() == null) ? false
                    : true;
            if (isSolrCloud) {
              controller = ((ZkSolrResourceLoader) solrCore.getResourceLoader())
                      .getZkController();
            }
            responseWriter = solrCore
                    .getQueryResponseWriter(responseWriterName);
            contentType = responseWriter.getContentType(getSolrRequest(),
                    getSolrResponse());
            solrResponse = solrRequestInfo.getRsp();
          }
        }
      } catch (Throwable t) {
        setExisting(false);
        throw new ResourceException(t);
      } finally {
        SolrRequestInfo.clearRequestInfo();
      }
    }
  }

  public SolrQueryRequest getSolrRequest() {
    return solrRequest;
  }

  public SolrQueryResponse getSolrResponse() {
    return solrResponse;
  }

  public CoreContainer getCoreContainer() {
    return coreContainer;
  }

  public static Charset getUtf8() {
    return UTF8;
  }

  public boolean isDoIndent() {
    return doIndent;
  }

  public boolean isSolrCloud() {
    return isSolrCloud;
  }

  @Override
  public QueryResponseWriter getResponseWriter() {
    return responseWriter;
  }

  @SuppressWarnings({ "rawtypes" })
  protected Map<String, Object> processPluginInfo(PluginInfo node, boolean isBatch) {
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    int size = node.initArgs.size();
    
    if(isBatch)
      response.put("name", node.name);
    response.put("class", node.className);
    
    for (int counter = 0; counter < size; counter++) {
      Object value = node.initArgs.getVal(counter);
      String name = node.initArgs.getName(counter);
      Map<String, Object> params = new LinkedHashMap<String, Object>();
      if (value instanceof NamedList) {
        processLst((NamedList) node.initArgs.getVal(counter), params);
        response.put(name, params);
      } else if (value instanceof List) {
        response.put(name, (List) value);
      }
    }

    return response;
  }

  @SuppressWarnings("rawtypes")
  protected void processLst(NamedList namedList, Map<String, Object> params) {
    int size = namedList.size();
    for (int counter = 0; counter < size; counter++) {
      Object value = namedList.getVal(counter);
      params.put(namedList.getName(counter), value);
    }
  }

  protected File getBackupFile() throws IOException {
    File currentSolrConfig = null;
    File backup = null;

    if (!isSolrCloud) {
      currentSolrConfig = new File(solrCore.getResourceLoader().getConfigDir()
              + File.separator + solrCore.getConfigResource());
      backup = new File(FileUtils.getTempDirectory(),
              solrCore.getConfigResource() + "." + System.currentTimeMillis()
                      + ".backup");

      try {
        FileUtils.copyFile(currentSolrConfig, backup);
      } catch (IOException e) {
        errors.add(ValidateUtils
                .buildErrorMessage(
                        "An error happened while copying solrconfig.xml file. For safety, no actions can be done until the copy can be created",
                        null, null));
        throw e;
      }
    }
    
    return backup;
  }

  protected void validateAndSave(EditableSolrConfig editableSolrConfig,
          File backup) throws IOException, BadVersionException,
          SolrConfigException, InterruptedException, KeeperException {
    boolean finish = true;

    if (isSolrCloud()
            && !CoreApi.validateCoreReload(editableSolrConfig.getDocument(),
                    solrCore)) {
      getSolrResponse().add("errors", "solrconfig.xml file not updated");
      finish = false;
    }

    if (finish) {
      editableSolrConfig.save();
      CoreApi.reload(getCoreContainer(), solrCore, isSolrCloud(), backup);
      getSolrResponse().add("response", "solrconfig.xml file updated");
    }
  }
}
