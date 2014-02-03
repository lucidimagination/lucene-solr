package org.apache.solr.rest.config.utils;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.SolrCore;
import org.apache.solr.rest.config.enumeration.SolrRequestHandlerParam;
import org.apache.solr.rest.config.exception.SolrConfigException;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.BadVersionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class EditableSolrConfig {

  private static final Logger log = LoggerFactory.getLogger(EditableSolrConfig.class);

  private SolrConfig solrConfig;
  private Document document;
  private SolrCore solrCore;
  private String instanceDir;
  private boolean isSolrCloud;

  private final static String SOLRCONFIG_FILE = "solrconfig.xml";

  public EditableSolrConfig(SolrCore solrCore, boolean isSolrCloud) {

    this.solrConfig = solrCore.getSolrConfig();
    this.document = (Document) this.solrConfig.getDocument().cloneNode(true);
    this.solrCore = solrCore;
    this.isSolrCloud = isSolrCloud;
    if (!isSolrCloud)
      this.instanceDir = solrConfig.getResourceLoader().getConfigDir();

  }

  public void save() throws IOException {

    if (isSolrCloud) {
      log.info("Solr cloud mode");
      log.info("ResourceLoader class: "
              + solrCore.getResourceLoader().getClass().getName());

      StringWriter writer = new StringWriter();
      XMLUtils.writeXml(document, writer);

      final byte[] data = writer.toString().getBytes("UTF-8");

      try {
        CoreApi.uploadSolrConfig(solrCore, data);
      } catch (BadVersionException e) {
        log.error("Error", e);
        throw new SolrException(ErrorCode.SERVER_ERROR,
                "Error persisting solrconfig.xml", e);
      } catch (InterruptedException e) {
        log.error("Error", e);
        throw new SolrException(ErrorCode.SERVER_ERROR,
                "Error persisting solrconfig.xml", e);
      } catch (KeeperException e) {
        log.error("Error", e);
        throw new SolrException(ErrorCode.SERVER_ERROR,
                "Error persisting solrconfig.xml", e);
      }
    } else {
      log.info("Solr simple mode");
      File outfile = new File(instanceDir + File.separator + SOLRCONFIG_FILE);
      XMLUtils.writeXml(document, outfile);
    }
  }

  @SuppressWarnings("unchecked")
  public void batchUpdateRequestHandler(Map<String, Object> data) throws SolrConfigException {
    for (String currentRequestHandlerName : data.keySet())
      updateRequestHandler(currentRequestHandlerName,
              (Map<String, Object>) data.get(currentRequestHandlerName));
  }

  @SuppressWarnings("unchecked")
  public void updateRequestHandler(String handlerName, Map<String, Object> data)
          throws SolrConfigException {

    Node requestHandler = XMLUtils.getRequestHandlerNode(handlerName, document);

    if (requestHandler == null)
      throw new RuntimeException("Request Handler " + handlerName
              + " does not exist");

    if (!ValidateUtils.validateXmlComponents(handlerName, data, document))
      throw new SolrConfigException(
              "First/Last components only valid if you do not declare components");

    removeRequestHandlerNodes(getNodesToremove(data, handlerName), handlerName);

    processRequestHandlerLst(requestHandler, handlerName,
            SolrRequestHandlerParam.DEFAULTS.getValue(),
            (Map<String, Object>) data.get(SolrRequestHandlerParam.DEFAULTS
                    .getValue()));

    processRequestHandlerLst(requestHandler, handlerName,
            SolrRequestHandlerParam.INVARIANTS.getValue(),
            (Map<String, Object>) data.get(SolrRequestHandlerParam.INVARIANTS
                    .getValue()));

    processRequestHandlerLst(requestHandler, handlerName,
            SolrRequestHandlerParam.APPENDS.getValue(),
            (Map<String, Object>) data.get(SolrRequestHandlerParam.APPENDS
                    .getValue()));

    processSearchComponent(requestHandler, handlerName,
            SolrRequestHandlerParam.COMPONENTS.getValue(),
            (List<String>) data.get(SolrRequestHandlerParam.COMPONENTS
                    .getValue()));

    processSearchComponent(requestHandler, handlerName,
            SolrRequestHandlerParam.FIRST_COMPONENTS.getValue(),
            (List<String>) data.get(SolrRequestHandlerParam.FIRST_COMPONENTS
                    .getValue()));

    processSearchComponent(requestHandler, handlerName,
            SolrRequestHandlerParam.LAST_COMPONENTS.getValue(),
            (List<String>) data.get(SolrRequestHandlerParam.LAST_COMPONENTS
                    .getValue()));
    
    processClass(requestHandler, data.get(SolrRequestHandlerParam.CLAZZ.getValue()));
  }

  private void processClass(Node requestHandler, Object clazz) {
    if(clazz == null)
      return;
    
    Element element = (Element) requestHandler;
    element.setAttribute("class", (String) clazz);
  }

  private void removeRequestHandlerNodes(List<Node> nodesToremove,
          String requestHandlerName) {
    Element rqNode = XMLUtils.getRequestHandlerNode(requestHandlerName, document);
    for (Node currentNode : nodesToremove)
      rqNode.removeChild(currentNode);
  }

  private List<Node> getNodesToremove(Map<String, Object> data,
          String requetHandlerName) {
    List<Node> response = new ArrayList<Node>();
    Node node = null;
        
    for (String key : data.keySet()) {
      log.info(key);
      if (data.get(key) instanceof Map && ((Map) data.get(key)).size() == 0) {
        node = XMLUtils.getRequestHandlerChildNode(requetHandlerName, key, document);
        if (node == null)
          log.info("Removal of "
                  + key
                  + " was requested, but it does not exist in current configuration. This request will be omited");
        else {
          response.add(node);
        }
      }

      else if (data.get(key) instanceof List
              && ((List) data.get(key)).size() == 0) {
        node = XMLUtils.getRequestHandlerArrNode(requetHandlerName, key, document);
        if (node == null)
          log.info("Removal of "
                  + key
                  + " was requested, but it does not exist in current configuration. This request will be omited");
        else {
          response.add(node);
        }
      }
    }

    return response;
  }

  private void processSearchComponent(Node requestHandler, String handlerName,
          String searchComponentName, List<String> list) {

    if (list == null || list.isEmpty())
      return;

    for (String name : list) {
      solrCore.getSearchComponent(name);
    }

    Node arrNode = XMLUtils.getRequestHandlerArrNode(handlerName, searchComponentName, document);

    // remove old arr node if it exists
    if (arrNode != null) {
      requestHandler.removeChild(arrNode);
      arrNode.setNodeValue(null);
    }

    // add new arr
    Element arrElement = document.createElement("arr");
    arrElement.setAttribute("name", searchComponentName);
    requestHandler.appendChild(arrElement);

    // add string values
    for (String value : list) {
      Element stringElement = document.createElement("str");
      stringElement.setTextContent(value);
      arrElement.appendChild(stringElement);
    }
  }

  private void processRequestHandlerLst(Node requestHandlerNode,
          String handlerName, String lstName, Map<String, Object> data) {

    if (data == null || data.isEmpty())
      return;

    log.info("Adding data: " + data.toString());

    Node lstNode = XMLUtils.getRequestHandlerChildNode(handlerName, lstName, document);
    if (lstNode == null) {
      log.info(lstName + " not FOUND");

    } else {
      requestHandlerNode.removeChild(lstNode);

    }

    Element newLstNode = document.createElement("lst");
    newLstNode.setAttribute("name", lstName);
    requestHandlerNode.appendChild(newLstNode);

    for (String currentKey : data.keySet()) {
      Object value = data.get(currentKey);
      String nodeType = ValidateUtils.getParamDataType(value).getValue();
      Element newProperty = document.createElement(nodeType);
      newProperty.setAttribute("name", "" + currentKey);
      newProperty.setTextContent("" + value);
      newLstNode.appendChild(newProperty);
    }
  }

  public Document getDocument() {
    return document;
  }
}
