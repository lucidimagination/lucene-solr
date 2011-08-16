#/**
# * Licensed to the Apache Software Foundation (ASF) under one or more
# * contributor license agreements.  See the NOTICE file distributed with
# * this work for additional information regarding copyright ownership.
# * The ASF licenses this file to You under the Apache License, Version 2.0
# * (the "License"); you may not use this file except in compliance with
# * the License.  You may obtain a copy of the License at
# *
# *     http://www.apache.org/licenses/LICENSE-2.0
# *
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.
# */
# -------------------------------------------------------------------------------------
# multi val params are iterated by NewRound's, added to reports, start with column name.
#
# based on micro-standard
#
# modified to use wikipedia sources and index entire docs
# currently just used to measure ingest rate


log.step=5000

docs.file=/home/mark/wikidumps/enwiki.out.txt
content.source=org.apache.lucene.benchmark.byTask.feeds.LineDocSource

solr.server=org.apache.solr.client.solrj.impl.StreamingUpdateSolrServer
#solr.server=org.apache.solr.client.solrj.impl.CommonsHttpSolrServer
#solr.url=http://localhost:8888/solr
solr.streaming.server.queue.size=10
solr.streaming.server.threadcount=5

solr.configs.home=/home/mark/LWEConfigs/filters
#solr.config.dir=/home/mark/LWEInstalls/LWE/conf/solr/cores/collection1_0/conf
solr.schema=schema:schema_ascii_folding.xml:schema_lowercase.xml:schema_metaphone.xml:schema_wdf.xml:schema_solr_example.xml:schema_porter.xml:schema_no_filter.xml

solr.field.mappings=doctitle>title,docid>id,docname>body,docdate>body,docdatenum>body,doctimesecnum>body


query.maker=org.apache.lucene.benchmark.byTask.feeds.ReutersQueryMaker

# task at this depth or less would print when they start
task.max.depth.log=2

log.queries=false
# -------------------------------------------------------------------------------------

{ "Rounds"

	solr.StopSolrServer
	
    ResetSystemErase
    
    solr.StartSolrServer
    
    solr.SolrClearIndex
    
      [ 
          { solr.SolrAddDoc > : 500
 
      ] : 1
      { solr.BlockSolrServerTillFinish >
      
    solr.SolrCommit
    solr.SolrClearIndex
    ResetSystemErase
    
    {"Populate"
      [ 
          { "MAddDocs" SolrAddDoc > : 10000
 
      ] : 1
      { "BlockTillFinish" solr.BlockSolrServerTillFinish >
    }
    
	solr.SolrCommit
	
	solr.StopSolrServer
		
    NewRound
    

} : 7

RepSumByName
RepSumByPrefRound MAddDocs
RepSumByPrefRound Populate
