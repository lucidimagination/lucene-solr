package org.apache.solr.rest.config;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.solr.common.SolrException;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.rest.config.exception.SolrConfigException;
import org.apache.solr.rest.config.utils.EditableSolrConfig;
import org.apache.solr.rest.config.utils.ValidateUtils;
import org.noggit.JSONParser.ParseException;
import org.noggit.ObjectBuilder;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unchecked")
public class RequestHandlerResource extends BaseConfigResource {

  private String handlerName;

  private static Logger log = LoggerFactory
          .getLogger(RequestHandlerResource.class);

  @Override
  public void doInit() {
    super.doInit();
    Reference rootRef = getRequest().getRootRef().addSegment("requestHandlers");
    handlerName = getRequest().getResourceRef().getRelativeRef(rootRef)
            .getPath();
    handlerName = "/" + handlerName;
  }

  @Override
  public Representation get() {
    SolrConfig solrConfig = solrCore.getSolrConfig();

    List<PluginInfo> nodeList = solrConfig
            .getPluginInfos(SolrRequestHandler.class.getName());
    for (PluginInfo currentNode : nodeList) {
      String currentReqHandlerName = currentNode.name;
      if (currentReqHandlerName.equals(handlerName)) {
        Map<String, Object> response = processPluginInfo(currentNode, false);
        for (String key : response.keySet())
          getSolrResponse().add(key, response.get(key));
        return new SolrOutputRepresentation(this, contentType);
      }
    }

    getSolrResponse().add("Error",
            "The request handler " + handlerName + " does not exist");
    return new SolrOutputRepresentation(this, contentType);
  }

  @Override
  public Representation put(Representation entity) {

    try {
      Object object = ObjectBuilder.fromJSON(entity.getText());

      if (!ValidateUtils.validateSingleUpdateObject(object, errors)) {
        getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        getSolrResponse().add("errors", errors);
      } else {
        File backup = getBackupFile();

        EditableSolrConfig editableSolrConfig = new EditableSolrConfig(
                solrCore, isSolrCloud());
        editableSolrConfig.updateRequestHandler(handlerName,
                (Map<String, Object>) object);

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
