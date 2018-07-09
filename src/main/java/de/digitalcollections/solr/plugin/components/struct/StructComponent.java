package de.digitalcollections.solr.plugin.components.struct;

import de.digitalcollections.lucene.analysis.payloads.PayloadSchema;
import de.digitalcollections.lucene.analysis.payloads.PayloadStruct;
import de.digitalcollections.lucene.analysis.payloads.StructParser;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoaderAware;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.handler.component.ShardRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.SolrPluginUtils;
import org.apache.solr.util.plugin.PluginInfoInitialized;

import java.io.IOException;
import java.util.*;

public class StructComponent extends SearchComponent implements PluginInfoInitialized, ResourceLoaderAware {
  private static final IndexSearcher EMPTY_INDEXSEARCHER;

  static {
    try {
      IndexReader emptyReader = new MultiReader();
      EMPTY_INDEXSEARCHER = new IndexSearcher(emptyReader);
      EMPTY_INDEXSEARCHER.setQueryCache(null);
    } catch (IOException bogus) {
      throw new RuntimeException(bogus);
    }
  }

  private String schemaPath;
  private StructParser parser;

  @Override
  public void init(PluginInfo info) {
    this.schemaPath = info.attributes.get("schema");
  }


  @Override
  public void inform(ResourceLoader loader) throws IOException {
    PayloadSchema schema = PayloadSchema.load(loader, this.schemaPath);
    this.parser = new StructParser(schema);
  }

  @Override
  public void prepare(ResponseBuilder rb) {
    // NOP
  }


  @Override
  public void process(ResponseBuilder rb) throws IOException {
    if (rb.req.getParams().getBool("structs", false)) {
      NamedList<Object> structs = fetchStructs(rb.getResults().docList, rb.getQuery(), rb.req);
      rb.rsp.add("struct_payloads", structs);
    }
  }


  // Adapted from solr's own HighlightComponent
  @Override
  public void modifyRequest(ResponseBuilder rb, SearchComponent who, ShardRequest sreq) {
    if (!(rb.req.getParams().getBool("structs", false))) return;

    // Turn on highlighting only only when retrieving fields
    if ((sreq.purpose & ShardRequest.PURPOSE_GET_FIELDS) != 0) {
      sreq.purpose |= ShardRequest.PURPOSE_GET_HIGHLIGHTS;
      // should already be true...
      sreq.params.set("structs", "true");     // TODO: Maybe set hl_params?
    } else {
      sreq.params.set("structs", "false");
    }
  }

  // Adapted from solr's own HighlightComponent
  @SuppressWarnings("unchecked")
  @Override
  public void finishStage(ResponseBuilder rb) {
    if (!rb.req.getParams().getBool("structs", false) || rb.stage != ResponseBuilder.STAGE_GET_FIELDS) {
      return;
    }

    NamedList.NamedListEntry[] arr = new NamedList.NamedListEntry[rb.resultIds.size()];
    rb.finished.stream()
        .filter(sreq -> (sreq.purpose & ShardRequest.PURPOSE_GET_HIGHLIGHTS) != 0)
        .flatMap(sreq -> sreq.responses.stream())
        // can't expect the highlight content if there was an exception for this request
        // this should only happen when using shards.tolerant=true
        .filter(resp -> resp.getException() == null)
        .map(resp -> (NamedList) resp.getSolrResponse().getResponse().get("struct_payloads"))
        .forEach(hl -> SolrPluginUtils.copyNamedListIntoArrayByDocPosInResponse(hl, rb.resultIds, arr));

    // remove nulls in case not all docs were able to be retrieved
    rb.rsp.add("struct_payloads", SolrPluginUtils.removeNulls(arr, new SimpleOrderedMap<>()));
  }


  @Override
  public String getDescription() {
    return null;
  }

  private Set<BytesRef> getTerms(Query query, String fieldName) throws IOException {
    Set<BytesRef> terms = new TreeSet<>();
    Set<Term> extractPosInsensitiveTermsTarget = new TreeSet<Term>() {
      @Override
      public boolean add(Term term) {
        if (term.field().equals(fieldName)) {
          return terms.add(term.bytes());
        }
        return false;
      }
    };
    query.createWeight(EMPTY_INDEXSEARCHER, false, 1.0f)
        .extractTerms(extractPosInsensitiveTermsTarget);
    return terms;
  }


  private NamedList<Object> fetchStructs(DocList docs, Query query, SolrQueryRequest req) throws IOException {
    SolrParams params = req.getParams();
    int maxStructsPerDoc = params.getInt("structs.maxPerDoc", -1);
    IndexReader reader = req.getSearcher().getIndexReader();

    int[] docIds = toDocIDs(docs);
    String[] keys = getUniqueKeys(req.getSearcher(), docIds);
    String[] fieldNames = params.getParams("structs.fields");

    // For each document, obtain a mapping from field names to their matching structs
    List<Map<String, List<PayloadStruct>>> boxes = new ArrayList<>();
    for (int docId : docIds) {
      Map<String, List<PayloadStruct>> docStructs = new HashMap<>();
      for (String fieldName : fieldNames) {
        // We grab the terms in their UTF-8 encoded form to avoid costly decoding operations
        // when checking for term equality down the line
        Set<BytesRef> termSet = getTerms(query, fieldName);
        List<PayloadStruct> structs = getMatchingStructs(reader, docId, fieldName, termSet, maxStructsPerDoc);
        docStructs.put(fieldName, structs);
      }
      boxes.add(docStructs);
    }
    return encodeSnippets(keys, fieldNames, boxes);
  }

  /**
   * Retrieve unique keys for matching documents.
   */
  private String[] getUniqueKeys(SolrIndexSearcher searcher, int[] docIds) throws IOException {
    IndexSchema schema = searcher.getSchema();
    SchemaField keyField = schema.getUniqueKeyField();
    if (keyField != null) {
      Set<String> selector = Collections.singleton(keyField.getName());
      String[] uniqueKeys = new String[docIds.length];
      for (int i = 0; i < docIds.length; i++) {
        int docId = docIds[i];
        Document doc = searcher.doc(docId, selector);
        String id = schema.printableUniqueKey(doc);
        uniqueKeys[i] = id;
      }
      return uniqueKeys;
    } else {
      return new String[docIds.length];
    }
  }

  /**
   * Retrieve Document IDs from the list of matching documents.
   */
  private int[] toDocIDs(DocList docs) {
    int[] ids = new int[docs.size()];
    DocIterator iterator = docs.iterator();
    for (int i = 0; i < ids.length; i++) {
      if (!iterator.hasNext()) {
        throw new AssertionError();
      }
      ids[i] = iterator.nextDoc();
    }
    if (iterator.hasNext()) {
      throw new AssertionError();
    }
    return ids;
  }

  private List<PayloadStruct> getMatchingStructs(
      IndexReader reader, int docId, String fieldName, Set<BytesRef> termSet, int maxPerDoc) throws IOException {
    List<PayloadStruct> structs = new ArrayList<>();

    final LeafReader leafReader;
    if (reader instanceof LeafReader) {
      leafReader = (LeafReader) reader;
    } else {
      List<LeafReaderContext> leaves = reader.leaves();
      LeafReaderContext leafReaderContext = leaves.get(ReaderUtil.subIndex(docId, leaves));
      leafReader = leafReaderContext.reader();
      docId -= leafReaderContext.docBase; // adjust 'doc' to be within this leaf reader
    }

    final Terms terms = leafReader.terms(fieldName);
    if (terms == null || !terms.hasPositions() || !terms.hasPayloads()) {
      return structs;
    }

    final TermsEnum termsEnum = terms.iterator();

    for (BytesRef term : termSet) {
      if (!termsEnum.seekExact(term)) {
        continue;
      }
      PostingsEnum postingsEnum = termsEnum.postings(null, PostingsEnum.POSITIONS | PostingsEnum.PAYLOADS);
      if (postingsEnum == null) {
        // no offsets or positions available
        throw new IllegalArgumentException("field '" + fieldName + "' was indexed without offsets, cannot highlight");
      }
      if (docId != postingsEnum.advance(docId)) {
        continue;
      }

      final int freq = postingsEnum.freq();
      for (int i = 0; i < freq && (maxPerDoc < 0 || structs.size() < maxPerDoc); i++) {
        postingsEnum.nextPosition();
        BytesRef payload = postingsEnum.getPayload();
        structs.add(parser.fromBytes(payload));
      }
    }
    return structs;
  }

  /**
   * Encode the highlighting result into a format that can be used by upstream users.
   */
  private NamedList<Object> encodeSnippets(String[] keys, String[] fieldNames,
                                           List<Map<String, List<PayloadStruct>>> structs) {
    NamedList<Object> list = new SimpleOrderedMap<>();
    for (int i = 0; i < keys.length; i++) {
      NamedList<Object> summary = new SimpleOrderedMap<>();
      Map<String, List<PayloadStruct>> docStructs = structs.get(i);
      for (String field : fieldNames) {
        summary.add(field, docStructs.get(field).stream().sorted().map(PayloadStruct::toNamedList));
      }
      list.add(keys[i], summary);
    }
    return list;
  }
}
