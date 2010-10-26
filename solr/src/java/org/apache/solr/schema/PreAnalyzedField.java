package org.apache.solr.schema;/** * Licensed to the Apache Software Foundation (ASF) under one or more * contributor license agreements.  See the NOTICE file distributed with * this work for additional information regarding copyright ownership. * The ASF licenses this file to You under the Apache License, Version 2.0 * (the "License"); you may not use this file except in compliance with * the License.  You may obtain a copy of the License at * *     http://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in writing, software * distributed under the License is distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. * See the License for the specific language governing permissions and * limitations under the License. */import java.io.ByteArrayOutputStream;import java.io.IOException;import java.io.Reader;import java.util.HashMap;import java.util.Iterator;import java.util.LinkedList;import java.util.List;import java.util.Map;import java.util.Map.Entry;import org.apache.lucene.analysis.Analyzer;import org.apache.lucene.analysis.TokenStream;import org.apache.lucene.analysis.Tokenizer;import org.apache.lucene.analysis.tokenattributes.FlagsAttribute;import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;import org.apache.lucene.analysis.tokenattributes.TypeAttribute;import org.apache.lucene.document.Field;import org.apache.lucene.document.Fieldable;import org.apache.lucene.index.Payload;import org.apache.lucene.search.SortField;import org.apache.lucene.util.Attribute;import org.apache.lucene.util.AttributeSource;import org.apache.solr.analysis.SolrAnalyzer;import org.apache.solr.response.TextResponseWriter;import org.apache.solr.response.XMLWriter;/** * Pre-analyzed field type provides a way to index a serialized token stream, * optionally with an independent stored value of a field. * <h2>Serialization format</h2> * <p>The format of the serialization is as follows: * <pre> * content ::= (stored)? tokens * ; stored field value - any "=" inside must be escaped! * stored ::= "=" text "=" * tokens ::= (token ((" ") + token)*)* * token ::= text ("," attrib)* * attrib ::= name '=' value * name ::= text * value ::= text * </pre> * <p>Special characters in "text" values can be escaped * using the escape character \ . The following escape sequences are recognized: * <pre> * "\ " - literal space character * "\," - literal , character * "\=" - literal = character * "\\" - literal \ character * "\n" - newline * "\r" - carriage return * "\t" - horizontal tab * </pre> * Please note that Unicode sequences (e.g. \u0001) are not supported. * <h2>Supported attribute names</h2> * The following token attributes are supported, and identified with short * symbolic names: * <pre> * i - position increment (integer) * s - token offset, start position (integer) * e - token offset, end position (integer) * t - token type (string) * f - token flags (hexadecimal integer) * p - payload (bytes in hexadecimal format) * </pre> * Token positions are tracked and implicitly added to the token stream -  * the start and end offsets consider only the term text and whitespace, * and exclude the space taken by token attributes. * <h2>Example token streams</h2> * <pre> * one two three  - stored: 'null'  - tok: '(term=one,startOffset=0,endOffset=3)'  - tok: '(term=two,startOffset=4,endOffset=7)'  - tok: '(term=three,startOffset=8,endOffset=13)' one  two   three   - stored: 'null'  - tok: '(term=one,startOffset=1,endOffset=4)'  - tok: '(term=two,startOffset=6,endOffset=9)'  - tok: '(term=three,startOffset=12,endOffset=17)'one,s=123,e=128,i=22  two three,s=20,e=22  - stored: 'null'  - tok: '(term=one,positionIncrement=22,startOffset=123,endOffset=128)'  - tok: '(term=two,positionIncrement=1,startOffset=5,endOffset=8)'  - tok: '(term=three,positionIncrement=1,startOffset=20,endOffset=22)'\ one\ \,,i=22,a=\, two\=  \n,\ =\   \  - stored: 'null'  - tok: '(term= one ,,positionIncrement=22,startOffset=0,endOffset=6)'  - tok: '(term=two=  ,positionIncrement=1,startOffset=7,endOffset=15)'  - tok: '(term=\,positionIncrement=1,startOffset=17,endOffset=18)',i=22 ,i=33,s=2,e=20 ,   - stored: 'null'  - tok: '(term=,positionIncrement=22,startOffset=0,endOffset=0)'  - tok: '(term=,positionIncrement=33,startOffset=2,endOffset=20)'  - tok: '(term=,positionIncrement=1,startOffset=2,endOffset=2)'=This is the stored part with \=  \n    \t escapes.=one two three   - stored: 'This is the stored part with =  \n    \t escapes.'  - tok: '(term=one,startOffset=0,endOffset=3)'  - tok: '(term=two,startOffset=4,endOffset=7)'  - tok: '(term=three,startOffset=8,endOffset=13)'==  - stored: ''  - (no tokens)=this is a test.=  - stored: 'this is a test.'  - (no tokens) * </pre> */public class PreAnalyzedField extends FieldType {    private class PreAnalyzedAnalyzer extends SolrAnalyzer {    @Override    public TokenStream tokenStream(String fieldName, Reader reader) {      PreAnalyzedTokenStream ts = new PreAnalyzedTokenStream();      try {        ts.reset(reader);      } catch (IOException e) {        // should not happen        throw new RuntimeException("should not happen");      }      return ts;    }    @Override    public TokenStreamInfo getStream(String fieldName, Reader reader) {      Tokenizer t = (Tokenizer)tokenStream(fieldName, reader);      TokenStreamInfo res = new TokenStreamInfo(t, t);      return res;    }      }  @Override  public Analyzer getAnalyzer() {    return new PreAnalyzedAnalyzer();  }    @Override  public Analyzer getQueryAnalyzer() {    return getAnalyzer();  }  @Override  public Field createField(SchemaField field, String externalVal,          float boost) {    Field f = null;    try {      f = fromString(field, externalVal, boost);    } catch (Exception e) {      e.printStackTrace();      return null;    }    return f;  }  @Override  protected void init(IndexSchema schema, Map<String, String> args) {    super.init(schema, args);  }  @Override  public SortField getSortField(SchemaField field, boolean top) {    return getStringSort(field, top);  }  @Override  public void write(XMLWriter xmlWriter, String name, Fieldable f)          throws IOException {        xmlWriter.writeStr(name, f.stringValue());  }  @Override  public void write(TextResponseWriter writer, String name, Fieldable f)          throws IOException {    writer.writeStr(name, f.stringValue(), true);  }    /** Utility method to convert a field to a string that is parse-able by this   * class.   * @param f field to convert   * @return string that is compatible with the serialization format   * @throws IOException   */  public static String toFormattedString(Fieldable f) throws IOException {    StringBuilder sb = new StringBuilder();    String s = f.stringValue();    if (s != null) {      // encode the equals sign      s = s.replaceAll("=", "\\=");      sb.append('=');      sb.append(s);      sb.append('=');    }    TokenStream ts = f.tokenStreamValue();    if (ts != null) {      StringBuilder tok = new StringBuilder();      boolean next = false;      while (ts.incrementToken()) {        if (next) {          sb.append(' ');        } else {          next = true;        }        tok.setLength(0);        Iterator<Class<? extends Attribute>> it = ts.getAttributeClassesIterator();        while (it.hasNext()) {          Class<? extends Attribute> cl = it.next();          Attribute att = ts.getAttribute(cl);          if (cl.isAssignableFrom(CharTermAttribute.class)) {            CharTermAttribute catt = (CharTermAttribute)att;            if (tok.length() > 0) {              tok.insert(0, escape(catt.buffer(), catt.length()) + ",");            } else {              tok.insert(0, escape(catt.buffer(), catt.length()));            }          } else {            if (tok.length() > 0) tok.append(',');            if (cl.isAssignableFrom(FlagsAttribute.class)) {              tok.append("f=" + Integer.toHexString(((FlagsAttribute)att).getFlags()));            } else if (cl.isAssignableFrom(OffsetAttribute.class)) {              tok.append("s=" + ((OffsetAttribute)att).startOffset() + ",e=" + ((OffsetAttribute)att).endOffset());            } else if (cl.isAssignableFrom(PayloadAttribute.class)) {              Payload p = ((PayloadAttribute)att).getPayload();              tok.append("p=" + bytesToHex(p.getData(), p.getOffset(), p.length()));            } else if (cl.isAssignableFrom(PositionIncrementAttribute.class)) {              tok.append("i=" + ((PositionIncrementAttribute)att).getPositionIncrement());            } else if (cl.isAssignableFrom(TypeAttribute.class)) {              tok.append("t=" + escape(((TypeAttribute)att).type()));            } else {                            tok.append(cl.getName() + "=" + escape(att.toString()));            }          }        }        sb.append(tok);      }    }    return sb.toString();  }    static String escape(String val) {    return escape(val.toCharArray(), val.length());  }    static String escape(char[] val, int len) {    if (val == null || len == 0) {      return "";    }    StringBuilder sb = new StringBuilder();    for (int i = 0; i < len; i++) {      switch (val[i]) {      case '\\' :      case '=' :      case ',' :      case ' ' :        sb.append('\\');        sb.append(val[i]);        break;      case '\n' :        sb.append('\\');        sb.append('n');        break;      case '\r' :        sb.append('\\');        sb.append('r');        break;      case '\t' :        sb.append('\\');        sb.append('t');        break;      default:        sb.append(val[i]);      }    }    return sb.toString();  }    // --------------------------------  // parser and related classes    private static class Tok {    StringBuilder token = new StringBuilder();    Map<String, String> attr = new HashMap<String, String>();        public boolean isEmpty() {      return token.length() == 0 && attr.size() == 0;    }        public void reset() {      token.setLength(0);      attr.clear();    }        public String toString() {      return "tok='" + token + "',attr=" + attr;    }  }    // parser state  private static enum S {TOKEN, NAME, VALUE, UNDEF};    private static final class ParseResult {    TokenStream ts;    String stored;  }    public Field fromString(SchemaField field, String val, float boost) throws Exception {    if (val == null || val.trim().length() == 0) {      return null;    }    ParseResult parse = fromString(val);    Field f = super.createField(field, val, boost);    if (parse.stored != null) {      f.setValue(parse.stored);    } else {      f.setValue((String)null);    }    if (parse.ts != null) {      f.setTokenStream(parse.ts);    }    return f;  }    ParseResult fromString(String val) throws Exception {    // first consume the optional stored part    int tsStart = 0;    boolean hasStored = false;    StringBuilder stored = new StringBuilder();    if (val.charAt(0) == '=') {      hasStored = true;      if (val.length() > 1) {        for (int i = 1; i < val.length(); i++) {          char c = val.charAt(i);          if (c == '\\') {            if (i < val.length() - 1) {              c = val.charAt(++i);              if (c == '=') { // we recognize only \= escape in the stored part                stored.append('=');              } else {                stored.append('\\');                stored.append(c);                continue;              }            } else {              stored.append(c);              continue;            }          } else if (c == '=') {            // end of stored text            tsStart = i + 1;            break;          } else {            stored.append(c);          }        }        if (tsStart == 0) { // missing end-of-stored marker          throw new Exception("Missing end marker of stored part");        }      } else {        throw new Exception("Unexpected end of stored field");      }    }    PreAnalyzedTokenStream pats = new PreAnalyzedTokenStream();    Tok tok = new Tok();    StringBuilder attName = new StringBuilder();    StringBuilder attVal = new StringBuilder();    // parser state    S s = S.UNDEF;    int lastPos = 0;    for (int i = tsStart; i < val.length(); i++) {      char c = val.charAt(i);      if (c == ' ') {        // collect leftovers        switch (s) {        case VALUE :          if (attVal.length() == 0) {            throw new Exception("Unexpected character '" + c + "' at position " + i + " - empty value of attribute.");          }          if (attName.length() > 0) {            tok.attr.put(attName.toString(), attVal.toString());          }          break;        case NAME: // attr name without a value ?          if (attName.length() > 0) {            throw new Exception("Unexpected character '" + c + "' at position " + i + " - missing attribute value.");          } else {            // accept missing att name and value          }          break;        case TOKEN:        case UNDEF:          // do nothing, advance to next token        }        attName.setLength(0);        attVal.setLength(0);        if (!tok.isEmpty() || s == S.NAME) {          AttributeSource.State state = createState(pats, tok, lastPos);          if (state != null) pats.addState(state);        }        // reset tok        s = S.UNDEF;        tok.reset();        // skip        lastPos++;        continue;      }      StringBuilder tgt = null;      switch (s) {      case TOKEN:        tgt = tok.token;        break;      case NAME:        tgt = attName;        break;      case VALUE:        tgt = attVal;        break;      case UNDEF:        tgt = tok.token;        s = S.TOKEN;      }      if (c == '\\') {        if (s == S.TOKEN) lastPos++;        if (i >= val.length() - 1) { // end                    tgt.append(c);          continue;        } else {          c = val.charAt(++i);          switch (c) {          case '\\' :          case '=' :          case ',' :          case ' ' :            tgt.append(c);            break;          case 'n':            tgt.append('\n');            break;          case 'r':            tgt.append('\r');            break;          case 't':            tgt.append('\t');            break;          default:            tgt.append('\\');            tgt.append(c);            lastPos++;          }        }      } else {        // state switch        if (c == ',') {          if (s == S.TOKEN) {            s = S.NAME;          } else if (s == S.VALUE) { // end of value, start of next attr            if (attVal.length() == 0) {              throw new Exception("Unexpected character '" + c + "' at position " + i + " - empty value of attribute.");            }            if (attName.length() > 0 && attVal.length() > 0) {              tok.attr.put(attName.toString(), attVal.toString());            }            // reset            attName.setLength(0);            attVal.setLength(0);            s = S.NAME;          } else {            throw new Exception("Unexpected character '" + c + "' at position " + i + " - missing attribute value.");          }        } else if (c == '=') {          if (s == S.NAME) {            s = S.VALUE;          } else {            throw new Exception("Unexpected character '" + c + "' at position " + i + " - empty value of attribute.");          }        } else {          tgt.append(c);          if (s == S.TOKEN) lastPos++;        }      }    }    // collect leftovers    if (!tok.isEmpty() || s == S.NAME || s == S.VALUE) {      // remaining attrib?      if (s == S.VALUE) {        if (attName.length() > 0 && attVal.length() > 0) {          tok.attr.put(attName.toString(), attVal.toString());        }              }      AttributeSource.State state = createState(pats, tok, lastPos);      if (state != null) pats.addState(state);    }    ParseResult res = new ParseResult();    if (hasStored) {      res.stored = stored.toString();    }    res.ts = pats;    return res;  }    private static AttributeSource.State createState(AttributeSource a, Tok state, int tokenEnd) {    a.clearAttributes();    CharTermAttribute termAtt = a.addAttribute(CharTermAttribute.class);    char[] tokChars = state.token.toString().toCharArray();    termAtt.copyBuffer(tokChars, 0, tokChars.length);    int tokenStart = tokenEnd - state.token.length();    for (Entry<String, String> e : state.attr.entrySet()) {      String k = e.getKey();      if (k.equals("i")) {        // position increment        int incr = Integer.parseInt(e.getValue());        PositionIncrementAttribute posIncr = a.addAttribute(PositionIncrementAttribute.class);        posIncr.setPositionIncrement(incr);      } else if (k.equals("s")) {        tokenStart = Integer.parseInt(e.getValue());      } else if (k.equals("e")) {        tokenEnd = Integer.parseInt(e.getValue());      } else if (k.equals("t")) {        TypeAttribute type = a.addAttribute(TypeAttribute.class);        type.setType(e.getValue());      } else if (k.equals("f")) {        FlagsAttribute flags = a.addAttribute(FlagsAttribute.class);        int f = Integer.parseInt(e.getValue(), 16);        flags.setFlags(f);      } else if (k.equals("p")) {        PayloadAttribute p = a.addAttribute(PayloadAttribute.class);        byte[] data = hexToBytes(e.getValue());        if (data != null && data.length > 0) {          p.setPayload(new Payload(data));        }      } else {        // unknown attribute      }    }    // handle offset attr    OffsetAttribute offset = a.addAttribute(OffsetAttribute.class);    offset.setOffset(tokenStart, tokenEnd);    //System.out.println(a);    return a.captureState();  }    private static final byte[] EMPTY_BYTES = new byte[0];    /** Utility method to convert byte array to a hex string. */  static byte[] hexToBytes(String hex) {    if (hex == null) {      return EMPTY_BYTES;    }    hex = hex.replaceAll("\\s+", "");    if (hex.length() == 0) {      return EMPTY_BYTES;    }    ByteArrayOutputStream baos = new ByteArrayOutputStream(hex.length() / 2);    byte b;    for (int i = 0; i < hex.length(); i++) {      int high = charToNibble(hex.charAt(i));      int low = 0;      if (i < hex.length() - 1) {        i++;        low = charToNibble(hex.charAt(i));      }      b = (byte)(high << 4 | low);      baos.write(b);    }    return baos.toByteArray();  }  static final int charToNibble(char c) {    if (c >= '0' && c <= '9') {      return c - '0';    } else if (c >= 'a' && c <= 'f') {      return 0xa + (c - 'a');    } else if (c >= 'A' && c <= 'F') {      return 0xA + (c - 'A');    } else {      throw new RuntimeException("Not a hex character: '" + c + "'");    }  }    static String bytesToHex(byte bytes[], int offset, int length) {    StringBuilder sb = new StringBuilder();    for (int i = offset; i < offset + length; ++i) {      sb.append(Integer.toHexString(0x0100 + (bytes[i] & 0x00FF))                       .substring(1));    }    return sb.toString();  }  /**   * Token stream that works from a list of saved states.   */  private class PreAnalyzedTokenStream extends Tokenizer {    private List<AttributeSource.State> cachedStates = new LinkedList<AttributeSource.State>();    private Iterator<AttributeSource.State> it = null;        public PreAnalyzedTokenStream() {      super();    }        private void addState(AttributeSource.State state) {      cachedStates.add(state);    }        public final boolean incrementToken() throws IOException {      // lazy init the iterator      if (it == null) {        it = cachedStates.iterator();      }          if (!it.hasNext()) {        return false;      }            AttributeSource.State state = (State) it.next();      restoreState(state);      return true;    }      public final void reset() {      it = cachedStates.iterator();    }    public void reset(Reader input) throws IOException {      StringBuilder sb = new StringBuilder();      char[] buf = new char[128];      int cnt;      try {        while ((cnt = input.read(buf)) > 0) {          sb.append(buf, 0, cnt);        }        PreAnalyzedTokenStream ts = (PreAnalyzedTokenStream)fromString(sb.toString()).ts;        // clone to this instance        cachedStates = ts.cachedStates;        it = null;      } catch (Exception e) {        // shouldn't happen      }    }  }}