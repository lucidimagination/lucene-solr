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

analyzer=org.apache.lucene.analysis.standard.StandardAnalyzer
directory=FSDirectory

doc.stored=true
doc.body.stored=false
doc.tokenized=false
doc.body.tokenized=true
doc.term.vector=false
log.step.SolrAddDoc=500
log.step.Search=10000
compound=false

#solr.server=org.apache.solr.client.solrj.impl.StreamingUpdateSolrServer
solr.server=org.apache.solr.client.solrj.impl.CommonsHttpSolrServer
solr.streaming.server.queue.size=10
solr.streaming.server.threadcount=5

solr.internal.server.xmx=1000M

file.query.maker.default.field=body_txt

solr.field.mappings=doctitle>title,docid>id,docname>body_txt,docdate>body_txt,docdatenum>body_txt,doctimesecnum>body_txt,body>body_txt


content.source=org.apache.lucene.benchmark.byTask.feeds.LineDocSource
content.source.forever=false
file.query.maker.file=/home/mark/queries.txt

query.maker=org.apache.lucene.benchmark.byTask.feeds.FileBasedQueryMaker
docs.file=/home/mark/wikidumps/enwiki.out.txt

content.source.forever=true

# task at this depth or less would print when they start
task.max.depth.log=2

log.queries=true

# -------------------------------------------------------------------------------------

{
  solr.StopSolrServer
	
  ResetSystemErase
    
  solr.StartSolrServer

  solr.SolrClearIndex
    
  [ "PreLoad" { solr.SolrAddDoc > : 50000] : 4
 
  solr.SolrCommit
  Wait(220)
    
  [ "WarmupSearches" { solr.SolrSearch > : 4 ] : 1
 
  # Get a new near-real-time reader, once per second:
  [ "UpdateIndexView" { solr.SolrCommit(soft) > : *] : 1 &

  # Index with 2 threads, each adding 100 docs per sec
  [ "Indexing" { solr.SolrAddDoc > : * : 20/sec ] : 2 &

  # Redline search (from queries.txt) with 4 threads
  [ "Searching" { solr.SolrSearch > : * ] : 4 &

  # Wait 60 sec, then wrap up
  Wait(60)
}

RepSumByPref Indexing
RepSumByPref Searching
RepSumByPref UpdateIndexView


