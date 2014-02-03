package org.apache.solr.rest.config.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class XMLUtils {

  private static final Logger log = LoggerFactory.getLogger(XMLUtils.class);
  private static final XPathFactory xpathFactory = XPathFactory.newInstance();
  public final static Map<String, String> ATTRIBS = new HashMap<String, String>();

  static {
    ATTRIBS.put(OutputKeys.METHOD, "xml");
    ATTRIBS.put(OutputKeys.INDENT, "yes");
    ATTRIBS.put("{http://xml.apache.org/xslt}indent-amount", "2");
  }

  public static void transform(Node node, Result result,
          Map<String, String> attributes) throws TransformerException {
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      transformer.setOutputProperty(entry.getKey(), entry.getValue());
    }

    DOMSource source = new DOMSource(node);

    transformer.transform(source, result);
  }

  public static Node getNode(String xpathStr, Node node)
          throws XPathExpressionException {
    XPath xpath = xpathFactory.newXPath();
    return (Node) xpath.evaluate(xpathStr, node, XPathConstants.NODE);
  }

  public static Element getRequestHandlerNode(String name, Document document) {
    Element result;
    try {
      result = (Element) XMLUtils.getNode("/config/requestHandler[@name='"
              + name + "']", document);

    } catch (XPathExpressionException e) {
      log.info("Error retrieving requestHandler: " + name);
      e.printStackTrace();
      throw new RuntimeException(e);
    }
    return result;
  }

  public static Element getRequestHandlerChildNode(String name, String childName, Document document) {
    Element result;
    try {
      result = (Element) XMLUtils.getNode("/config/requestHandler[@name='"
              + name + "']/lst[@name='" + childName + "']", document);
    } catch (XPathExpressionException e) {
      throw new RuntimeException(e);
    }
    return result;
  }

  public static Element getRequestHandlerArrNode(String name, String arrName, Document document) {
    Element result;
    try {
      result = (Element) XMLUtils.getNode("/config/requestHandler[@name='"
              + name + "']/arr[@name='" + arrName + "']", document);
    } catch (XPathExpressionException e) {
      throw new RuntimeException(e);
    }
    return result;
  }
  
  public static void writeXml(Node doc, File file) throws IOException {

    synchronized (XMLUtils.class) {
      FileOutputStream fis = new FileOutputStream(file);
      OutputStreamWriter writer = new OutputStreamWriter(fis,
              Charset.forName("UTF-8"));
      try {
        Result result = new StreamResult(writer);
        XMLUtils.transform(doc, result, ATTRIBS);
      } catch (TransformerException e) {
        throw new RuntimeException(e);
      } finally {
        writer.close();
        fis.close();
      }
    }
  }

  public static void writeXml(Node doc, StringWriter writer) throws IOException {

    synchronized (XMLUtils.class) {
      try {
        Result result = new StreamResult(writer);
        XMLUtils.transform(doc, result, ATTRIBS);
      } catch (TransformerException e) {
        throw new RuntimeException(e);
      } finally {
        writer.close();
      }
    }
  }
}
