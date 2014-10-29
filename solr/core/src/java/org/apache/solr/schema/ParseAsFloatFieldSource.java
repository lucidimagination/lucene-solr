package org.apache.solr.schema;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.docvalues.DocTermsIndexDocValues;
import org.apache.lucene.queries.function.valuesource.FieldCacheSource;

import java.io.IOException;
import java.util.Map;


/**
 * Parses float values from {@link DocTermsIndexDocValues#strVal(int)}, returning
 * zero for missing values and for values that fail to parse as floats.
 */
public class ParseAsFloatFieldSource extends FieldCacheSource {

  public ParseAsFloatFieldSource(String field) {
    super(field);
  }

  @Override
  public String description() {
    return "float(" + field + ')';
  }

  @Override
  public FunctionValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
    return new DocTermsIndexDocValues(this, readerContext, field) {

      @Override
      protected String toTerm(String readableValue) {
        return readableValue;
      }

      @Override
      public Object objectVal(int doc) {
        return floatVal(doc);
      }

      @Override
      public float floatVal(int doc) {
        String value = strVal(doc);
        if (value == null) {
          return 0f;
        }
        try {
          return Float.valueOf(value);
        } catch (NumberFormatException e) {
          return 0f;
        }
      }

      public double doubleVal(int doc) {
        return floatVal(doc);
      }

      @Override
      public String toString(int doc) {
        return (description() + "float(" + strVal(doc)+')');
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof StrFieldSource
            && super.equals(o);
  }

  private static int hcode = ParseAsFloatFieldSource.class.hashCode();
  @Override
  public int hashCode() {
    return hcode + super.hashCode();
  }
}

