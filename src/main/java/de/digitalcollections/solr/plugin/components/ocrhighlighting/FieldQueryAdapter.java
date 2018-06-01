package de.digitalcollections.solr.plugin.components.ocrhighlighting;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.vectorhighlight.FieldQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.UnicodeUtil;

/**
 * A small wrapper around {@link FieldQuery} to expose package-private constructors and methods to classes outside
 * of the package so we can obtain the set of query terms from a given query without jumping through too many hoops.
 */
@SuppressWarnings("unchecked")
class FieldQueryAdapter {
  // TODO: Here be dragons, lots of nasty reflection.... Isn't there a less evil way to get the term set? :-/

  private FieldQuery fq;

  FieldQueryAdapter(Query query, IndexReader reader) {
    Class<FieldQuery> clz = FieldQuery.class;
    try {
      Constructor<?> constructor = clz.getDeclaredConstructor(Query.class, IndexReader.class, boolean.class, boolean.class);
      constructor.setAccessible(true);
      this.fq = (FieldQuery) constructor.newInstance(query, reader, false, true);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
      throw new RuntimeException("Cannot initialize", e);
    }
  }

  private Set<String> getTermSet(String field) {
    try {
      Method method = this.fq.getClass().getDeclaredMethod("getTermSet", String.class);
      method.setAccessible(true);
      return (Set<String>) method.invoke(this.fq, field);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException("Cannot call getTermSet", e);
    }
  }

  /**
   * Get the terms for the field as UTF-8 encoded {@link BytesRef}s.
   */
  Set<BytesRef> getBytesTermSet(String field) {
    Set<BytesRef> refs = new HashSet<>();
    Set<String> terms = getTermSet(field);

    if (terms == null) {
      return refs;
    }

    // Preallocate continuous buffer to hold the encoded terms
    int targetLength = terms.stream().mapToInt(t -> UnicodeUtil.calcUTF16toUTF8Length(t, 0, t.length())).sum();
    byte[] buf = new byte[targetLength];


    int offset = 0;
    for (String term : terms) {
      int newOffset = UnicodeUtil.UTF16toUTF8(term, 0, term.length(), buf, offset);
      refs.add(new BytesRef(buf, offset, newOffset - offset));
      offset = newOffset;
    }
    return refs;
  }
}
