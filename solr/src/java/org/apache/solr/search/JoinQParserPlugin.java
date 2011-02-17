/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.search;

import org.apache.lucene.index.*;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.OpenBitSet;
import org.apache.lucene.util.StringHelper;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.TrieField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;


public class JoinQParserPlugin extends QParserPlugin {
  public static String NAME = "join";

  public void init(NamedList args) {
  }

  public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
    return new QParser(qstr, localParams, params, req) {
      public Query parse() throws ParseException {
        String fromField = getParam("from");
        String toField = getParam("to");
        String v = localParams.get("v");
        QParser fromQueryParser = subQuery(v, "lucene");
        Query fromQuery = fromQueryParser.getQuery();
        JoinQuery jq = new JoinQuery(fromField, toField, fromQuery);
        return jq;
      }
    };
  }
}



//
//  Enable directly getting internal DocSet ( for filter generation )
//  Always need to get ahold of top level searcher though.
//
//  Recursively call SolrIndexSearcher.getDocSet(q) since subquery could be another join query
//  what about scoring version though?
//
//  If used as the main query, the first time (should we wait for a call to weight.score()?)
//  we need to generate the whole docset.  Then return a scorer over a filter?
//
class JoinQuery extends Query {
  String fromField;
  String toField;
  Query q;

  public JoinQuery(String fromField, String toField, Query subQuery) {
    this.fromField = fromField;
    this.toField = toField;
    this.q = subQuery;
  }

  public Query getQuery() { return q; }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    Query newQ = q.rewrite(reader);
    if (newQ == q) return this;
    JoinQuery nq = (JoinQuery)this.clone();
    nq.q = newQ;
    return nq;
  }

  @Override
  public void extractTerms(Set terms) {
    q.extractTerms(terms);
  }

  public Weight createWeight(IndexSearcher searcher) throws IOException {
    return new JoinQueryWeight((SolrIndexSearcher)searcher);
  }

  private class JoinQueryWeight extends Weight {
    final SolrIndexSearcher searcher;
    private Similarity similarity;
    private float queryNorm;
    private float queryWeight;

    public JoinQueryWeight(SolrIndexSearcher searcher) throws IOException {
      this.searcher = searcher;
    }

    public Query getQuery() {
      return JoinQuery.this;
    }

    public float getValue() {
      return getBoost();
    }

    @Override
    public float sumOfSquaredWeights() throws IOException {
      queryWeight = getBoost();
      return queryWeight * queryWeight;
    }

    @Override
    public void normalize(float norm) {
      this.queryNorm = norm;
      queryWeight *= this.queryNorm;
    }

    DocSet resultSet;
    Filter filter;


    @Override
    public Scorer scorer(IndexReader.AtomicReaderContext context, ScorerContext scorerContext) throws IOException {
      if (filter == null) {
        resultSet = getDocSet();
        filter = resultSet.getTopFilter();
      }

      DocIdSet readerSet = filter.getDocIdSet(context);
      if (readerSet == null) readerSet=DocIdSet.EMPTY_DOCIDSET;
      return new JoinScorer(this, readerSet.iterator());
    }

    public DocSet getDocSet() throws IOException {
      OpenBitSet resultBits = null;

      // minimum docFreq to use the cache
      int minDocFreq = Math.max(5, searcher.maxDoc() >> 13);

      // use a smaller size than normal since we will need to sort and dedup the results
      int maxSortedIntSize = Math.max(10, searcher.maxDoc() >> 10);

      DocSet fromSet = searcher.getDocSet(q);
      List<DocSet> resultList = new ArrayList<DocSet>(10);
      int resultListDocs = 0;

      // make sure we have a set that is fast for random access, if we will use it for that
      DocSet fastForRandomSet = fromSet;
      if (minDocFreq>0 && fromSet instanceof SortedIntDocSet) {
        SortedIntDocSet sset = (SortedIntDocSet)fromSet;
        fastForRandomSet = new HashDocSet(sset.getDocs(), 0, sset.size());
      }

      Fields fields = MultiFields.getFields(searcher.getIndexReader());
      if (fields == null) return DocSet.EMPTY;
      Terms terms = fields.terms(fromField);
      Terms toTerms = fields.terms(toField);
      if (terms == null || toTerms==null) return DocSet.EMPTY;
      String prefixStr = TrieField.getMainValuePrefix(searcher.getSchema().getFieldType(fromField));
      BytesRef prefix = prefixStr == null ? null : new BytesRef(prefixStr);

      BytesRef term = null;
      TermsEnum  termsEnum = terms.iterator();
      TermsEnum  toTermsEnum = toTerms.iterator();
      SolrIndexSearcher.DocsEnumState fromDeState = null;
      SolrIndexSearcher.DocsEnumState toDeState = null;

      if (prefix == null) {
        term = termsEnum.next();
      } else {
        if (termsEnum.seek(prefix, true) != TermsEnum.SeekStatus.END) {
          term = termsEnum.term();
        }
      }

      Bits deletedDocs = MultiFields.getDeletedDocs(searcher.getIndexReader());

      fromDeState = new SolrIndexSearcher.DocsEnumState();
      fromDeState.fieldName = StringHelper.intern(fromField);
      fromDeState.deletedDocs = deletedDocs;
      fromDeState.termsEnum = termsEnum;
      fromDeState.docsEnum = null;
      fromDeState.minSetSizeCached = minDocFreq;

      toDeState = new SolrIndexSearcher.DocsEnumState();
      toDeState.fieldName = StringHelper.intern(toField);
      toDeState.deletedDocs = deletedDocs;
      toDeState.termsEnum = toTermsEnum;
      toDeState.docsEnum = null;
      toDeState.minSetSizeCached = minDocFreq;

      while (term != null) {
        if (prefix != null && !term.startsWith(prefix))
          break;

        boolean intersects = false;
        if (termsEnum.docFreq() < minDocFreq) {
          // OK to skip deletedDocs, since we check for intersection with docs matching query
          fromDeState.docsEnum = fromDeState.termsEnum.docs(null, fromDeState.docsEnum);
          DocsEnum docsEnum = fromDeState.docsEnum;

          if (docsEnum instanceof MultiDocsEnum) {
            MultiDocsEnum.EnumWithSlice[] subs = ((MultiDocsEnum)docsEnum).getSubs();
            int numSubs = ((MultiDocsEnum)docsEnum).getNumSubs();
            outer: for (int subindex = 0; subindex<numSubs; subindex++) {
              MultiDocsEnum.EnumWithSlice sub = subs[subindex];
              if (sub.docsEnum == null) continue;
              DocsEnum.BulkReadResult bulk = sub.docsEnum.getBulkResult();
              int base = sub.slice.start;
              for (;;) {
                int nDocs = sub.docsEnum.read();
                if (nDocs == 0) break;
                int[] docArr = bulk.docs.ints;  // this might be movable outside the loop, but perhaps not worth the risk.
                int end = bulk.docs.offset + nDocs;
                for (int i=bulk.docs.offset; i<end; i++) {
                  if (fastForRandomSet.exists(docArr[i]+base)) {
                    intersects = true;
                    break outer;
                  }
                }
              }
            }
          } else {
            // this should be the same bulk result object if sharing of the docsEnum succeeded
            DocsEnum.BulkReadResult bulk = docsEnum.getBulkResult();

            outer: for (;;) {
              int nDocs = docsEnum.read();
              if (nDocs == 0) break;
              int[] docArr = bulk.docs.ints;  // this might be movable outside the loop, but perhaps not worth the risk.
              int end = bulk.docs.offset + nDocs;
              for (int i=bulk.docs.offset; i<end; i++) {
                if (fastForRandomSet.exists(docArr[i])) {
                  intersects = true;
                  break outer;
                }
              }
            }
          }
        } else {
          // use the filter cache
          DocSet fromTermSet = searcher.getDocSet(fromDeState);
          intersects = fromSet.intersects(fromTermSet);
        }

        if (intersects) {
          TermsEnum.SeekStatus status = toTermsEnum.seek(term);
          if (status == TermsEnum.SeekStatus.END) break;
          if (status == TermsEnum.SeekStatus.FOUND) {

            int df = toTermsEnum.docFreq();
            if (resultBits==null && df + resultListDocs > maxSortedIntSize && resultList.size() > 0) {
              resultBits = new OpenBitSet(searcher.maxDoc());
            }

            // if we don't have a bitset yet, or if the resulting set will be too large
            // use the filterCache to get a DocSet
            if (toTermsEnum.docFreq() >= minDocFreq || resultBits == null) {
              // use filter cache
              DocSet toTermSet = searcher.getDocSet(toDeState);
              resultListDocs += toTermSet.size();
              if (resultBits != null) {
                toTermSet.setBitsOn(resultBits);
              } else {
                if (toTermSet instanceof BitDocSet) {
                  resultBits = (OpenBitSet)((BitDocSet)toTermSet).bits.clone();
                } else {
                  resultList.add(toTermSet);
                }
              }
            } else {
              // need to use deletedDocs here so we don't map to any deleted ones
              toDeState.docsEnum = toDeState.termsEnum.docs(toDeState.deletedDocs, toDeState.docsEnum);
              DocsEnum docsEnum = toDeState.docsEnum;              

              if (docsEnum instanceof MultiDocsEnum) {
                MultiDocsEnum.EnumWithSlice[] subs = ((MultiDocsEnum)docsEnum).getSubs();
                int numSubs = ((MultiDocsEnum)docsEnum).getNumSubs();
                for (int subindex = 0; subindex<numSubs; subindex++) {
                  MultiDocsEnum.EnumWithSlice sub = subs[subindex];
                  if (sub.docsEnum == null) continue;
                  DocsEnum.BulkReadResult bulk = sub.docsEnum.getBulkResult();
                  int base = sub.slice.start;
                  for (;;) {
                    int nDocs = sub.docsEnum.read();
                    if (nDocs == 0) break;
                    resultListDocs += nDocs;
                    int[] docArr = bulk.docs.ints;  // this might be movable outside the loop, but perhaps not worth the risk.
                    int end = bulk.docs.offset + nDocs;
                    for (int i=bulk.docs.offset; i<end; i++) {
                      resultBits.fastSet(docArr[i]+base);
                    }
                  }
                }
              } else {
                // this should be the same bulk result object if sharing of the docsEnum succeeded
                DocsEnum.BulkReadResult bulk = docsEnum.getBulkResult();

                for (;;) {
                  int nDocs = docsEnum.read();
                  if (nDocs == 0) break;
                  resultListDocs += nDocs;
                  int[] docArr = bulk.docs.ints;  // this might be movable outside the loop, but perhaps not worth the risk.
                  int end = bulk.docs.offset + nDocs;
                  for (int i=bulk.docs.offset; i<end; i++) {
                    resultBits.fastSet(docArr[i]);
                  }
                }
              }
            }

          }
        }

        term = termsEnum.next();
      }

      if (resultBits != null) {
        for (DocSet set : resultList) {
          set.setBitsOn(resultBits);
        }
        return new BitDocSet(resultBits);
      }

      if (resultList.size()==0) {
        return DocSet.EMPTY;
      }

      if (resultList.size() == 1) {
        return resultList.get(0);
      }

      int sz = resultList.size();

      for (DocSet set : resultList)
        sz += set.size();

      int[] docs = new int[sz];
      int pos = 0;
      for (DocSet set : resultList) {
        System.arraycopy(((SortedIntDocSet)set).getDocs(), 0, docs, pos, set.size());
        pos += set.size();
      }
      Arrays.sort(docs);
      int[] dedup = new int[sz];
      pos = 0;
      int last = -1;
      for (int doc : docs) {
        if (doc != last)
          dedup[pos++] = doc;
        last = doc;
      }

      if (pos != dedup.length) {
        dedup = Arrays.copyOf(dedup, pos);
      }

      return new SortedIntDocSet(dedup, dedup.length);
    }

    @Override
    public Explanation explain(IndexReader.AtomicReaderContext context, int doc) throws IOException {
      Scorer scorer = scorer(context, null);
      boolean exists = scorer.advance(doc) == doc;

      ComplexExplanation result = new ComplexExplanation();

      if (exists) {
        result.setDescription(this.toString()
        + " , product of:");
        result.setValue(queryWeight);
        result.setMatch(Boolean.TRUE);
        result.addDetail(new Explanation(getBoost(), "boost"));
        result.addDetail(new Explanation(queryNorm,"queryNorm"));
      } else {
        result.setDescription(this.toString()
        + " doesn't match id " + doc);
        result.setValue(0);
        result.setMatch(Boolean.FALSE);
      }
      return result;
    }
  }


  protected static class JoinScorer extends Scorer {
    final DocIdSetIterator iter;
    final float score;
    int doc = -1;

    public JoinScorer(Weight w, DocIdSetIterator iter) throws IOException {
      super(w);
      score = w.getValue();
      this.iter = iter==null ? DocIdSet.EMPTY_DOCIDSET.iterator() : iter;
    }

    @Override
    public int nextDoc() throws IOException {
      return iter.nextDoc();
    }

    @Override
    public int docID() {
      return iter.docID();
    }

    @Override
    public float score() throws IOException {
      return score;
    }

    @Override
    public int advance(int target) throws IOException {
      return iter.advance(target);
    }
  }


  @Override
  public String toString(String field) {
    return "{!join from="+fromField+" to="+toField+"}"+q.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (getClass() != o.getClass()) return false;
    JoinQuery other = (JoinQuery)o;
    return this.fromField.equals(other.fromField)
           && this.toField.equals(other.toField)
           && this.getBoost() == other.getBoost()
           && this.q.equals(other.q);
  }

  @Override
  public int hashCode() {
    int h = q.hashCode();
    h = h * 31 + fromField.hashCode();
    h = h * 31 + toField.hashCode();
    return h;
  }

}
