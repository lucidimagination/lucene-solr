package org.apache.solr.rest.config;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import org.apache.solr.common.util.ContentStreamBase;
import org.apache.solr.response.BinaryQueryResponseWriter;
import org.apache.solr.util.FastWriter;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.representation.OutputRepresentation;

/**
 * This class serves as an adapter between Restlet and Solr's response writers.
 */
public class SolrOutputRepresentation extends OutputRepresentation {
  private static final Charset UTF8 = Charset.forName("UTF-8");
  private CommonServerResource serverResource;
  private String contentType;

  SolrOutputRepresentation(CommonServerResource serverResource,
          String contentType) {
    // No normalization, in case of a custom media type
    super(MediaType.valueOf(contentType));
    this.serverResource = serverResource;
    this.contentType = contentType;
    // TODO: For now, don't send the Vary: header, but revisit if/when
    // content negotiation is added
    this.serverResource.getDimensions().clear();
  }

  /** Called by Restlet to get the response body */
  @Override
  public void write(OutputStream outputStream) throws IOException {
    if (serverResource.getRequest().getMethod() != Method.HEAD) {
      if (serverResource.getResponseWriter() instanceof BinaryQueryResponseWriter) {
        BinaryQueryResponseWriter binWriter = (BinaryQueryResponseWriter) serverResource
                .getResponseWriter();
        binWriter.write(outputStream, serverResource.getSolrRequest(),
                serverResource.getSolrResponse());
      } else {
        String charset = ContentStreamBase
                .getCharsetFromContentType(contentType);
        Writer out = (charset == null || charset.equalsIgnoreCase("UTF-8")) ? new OutputStreamWriter(
                outputStream, UTF8) : new OutputStreamWriter(outputStream,
                charset);
        out = new FastWriter(out);
        serverResource.getResponseWriter().write(out,
                serverResource.getSolrRequest(),
                serverResource.getSolrResponse());
        out.flush();
      }
    }
  }
}
