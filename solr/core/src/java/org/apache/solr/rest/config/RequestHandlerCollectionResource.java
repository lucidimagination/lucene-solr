package org.apache.solr.rest.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.solr.common.SolrException;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.rest.GETable;
import org.apache.solr.rest.PUTable;
import org.apache.solr.rest.config.exception.SolrConfigException;
import org.apache.solr.rest.config.utils.CoreApi;
import org.apache.solr.rest.config.utils.EditableSolrConfig;
import org.apache.solr.rest.config.utils.ValidateUtils;
import org.noggit.JSONParser.ParseException;
import org.noggit.ObjectBuilder;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestHandlerCollectionResource extends BaseConfigResource implements
        GETable, PUTable {

  private static Logger log = LoggerFactory
          .getLogger(RequestHandlerCollectionResource.class);

  public RequestHandlerCollectionResource() {
    super();
  }

  @Override
  public Representation get() {
    SolrConfig solrConfig = solrCore.getSolrConfig();

    List<PluginInfo> nodeList = solrConfig.readPluginInfos("requestHandler",
            true, true);

    List<Map<String, Object>> requestHandlers = new ArrayList<Map<String, Object>>();

    for (PluginInfo currentNode : nodeList) {
      Map<String, Object> requestHandler = processPluginInfo(currentNode, true);

      requestHandlers.add(requestHandler);
    }

    getSolrResponse().add("requestHandlers", requestHandlers);

    return new SolrOutputRepresentation(this, contentType);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Representation put(Representation entity) {

    try {
      Object object = ObjectBuilder.fromJSON(entity.getText());

      if (!ValidateUtils.validateBatchUpdateObject(object, errors)) {
        getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        getSolrResponse().add("errors", errors);
      } else {
        File backup = getBackupFile();

        EditableSolrConfig editableSolrConfig = new EditableSolrConfig(
                solrCore, isSolrCloud());
        editableSolrConfig
                .batchUpdateRequestHandler((Map<String, Object>) object);

        validateAndSave(editableSolrConfig, backup);
      }
    } catch (SolrException e) {
      log.error("SolrException", e);
      getSolrResponse().add("errors", e.getMessage());
    } catch (SolrConfigException e) {
      getSolrResponse().add("errors", e.getMessage());
    } catch (IOException e) {
      String msg = "Error while updating solrconfig.xml";
      log.error(msg, e);
      getSolrResponse().add("errors", msg);
    } catch (ParseException e) {
      String msg = "JSON body malformed";
      log.error(msg, e);
      getSolrResponse().add("errors", msg);
    } catch (Throwable e) {
      String msg = "Internal Error, please verify logs";
      log.error(msg, e);
      getSolrResponse().add("errors", msg);
    }

    return new SolrOutputRepresentation(this, contentType);
  }
}