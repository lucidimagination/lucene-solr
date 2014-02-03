package org.apache.solr.rest.config;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRequestHandlerCollectionResource extends SolrConfigRestTestBase {

  static Logger log = LoggerFactory
          .getLogger(TestRequestHandlerCollectionResource.class);

  @Test
  public void testListRequestHandlers() throws Exception {

    log.info("============ testGetBatchRequestHandlers ============");

    assertJQ(
            "/config/requestHandlers",
            "/requestHandlers/[0]=={'name':'/select','class':'solr.SearchHandler','defaults':{'echoParams':'explicit','rows':5,'df':'text',"
                    + "'facet':true}}",
            "/requestHandlers/[1]=={'name':'/query','class':'solr.SearchHandler','defaults':{'echoParams':'explicit','wt':'json','indent':'true',"
                    + "'df':'text'}}",
            "/requestHandlers/[2]=={'name':'/get','class':'solr.RealTimeGetHandler','defaults':{'omitHeader':'true','wt':'json','indent':'true'}}",
            "/requestHandlers/[14]=={'name':'/spell','class':'solr.SearchHandler','defaults':{'df':'text','spellcheck.dictionary':'wordbreak',"
                    + "'spellcheck':'on','spellcheck.extendedResults':'true','spellcheck.count':'10','spellcheck.alternativeTermCount':'5',"
                    + "'spellcheck.maxResultsForSuggest':'5','spellcheck.collate':'true','spellcheck.collateExtendedResults':'true',"
                    + "'spellcheck.maxCollationTries':'10','spellcheck.maxCollations':'5'},'last-components':['spellcheck']}",
            "/requestHandlers/[15]=={'name':'/tvrh','class':'solr.SearchHandler','defaults':{'df':'text','tv':true}, 'last-components':['tvComponent']}",
            "/requestHandlers/[16]=={'name':'/js','class':'org.apache.solr.handler.js.JavaScriptRequestHandler'}",
            "/requestHandlers/[17]=={'name':'/terms','class':'solr.SearchHandler','defaults':{'terms':true,'distrib':false}, 'components':['terms']}");
  }

  @Test
  public void testUpdateBatchRequestHandlerJSONWrongStructureLst()
          throws Exception {

    log.info("============ testUpdateBatchRequestHandlerJSONWrongStructure ============");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : 'defaults'}"),
            "/errors/[0]/message=='Format error.  Cause: java.lang.String cannot be cast to java.util.Map'",
            "/errors/[0]/object with problem=='defaults'",
            "/errors/[0]/expected format=={'defaults':{},'appends':{},'invariants':{}}");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'defaults':'wrong data'}}"),
            "/errors/[0]/message=='Format error.  Cause: java.lang.String cannot be cast to java.util.Map'",
            "/errors/[0]/object with problem=='wrong data'",
            "/errors/[0]/expected format=={'echoParams':'all','rows':'20','df':'title'}");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'defaults':['wrong data']}}"),
            "/errors/[0]/message=='Format error.  Cause: java.util.ArrayList cannot be cast to java.util.Map'",
            "/errors/[0]/object with problem/[0]=='wrong data'",
            "/errors/[0]/expected format=={'echoParams':'all','rows':'20','df':'title'}");

    log.info("============ Standard error ============");
    assertJPut("/config/requestHandlers",
            json("{'/select' : {'defaults':[{'wrong data'}]}}"),
            "/errors=='JSON body malformed'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'defaults':[" + "{'wrong name':'wrong data'}, "
                    + "{'wrong name':'wrong data', 'value':'wrong data'},"
                    + "{'name':'wrong data', 'wrong value':'wrong data'}]}}"),
            "/errors/[0]/message=='Format error.  Cause: java.util.ArrayList cannot be cast to java.util.Map'",
            "/errors/[0]/object with problem/[0]=={'wrong name':'wrong data'},{'wrong name':'wrong data','value':'wrong data'},"
                    + "{'name':'wrong data','wrong value':'wrong data'}",
            "/errors/[0]/expected format=={'echoParams':'all','rows':'20','df':'title'}");
  }

  @Test
  public void testUpdateBatchRequestHandlerJSONWrongStructureComponents()
          throws Exception {
    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'components':['query'], 'first-components':['facet']}}"),
            "/errors/[0]/message=='First/Last components only valid if you do not declare components'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'components':['query'], 'last-components':['facet']}}"),
            "/errors/[0]/message=='First/Last components only valid if you do not declare components'");

    assertJPut(
            "/config/requestHandlers",
            json("{'/select' : {'components':['query'], 'first-components':['facet'], "
                    + "'last-components':['mlt']}}"),
            "/errors/[0]/message=='First/Last components only valid if you do not declare components'");
  }
}
