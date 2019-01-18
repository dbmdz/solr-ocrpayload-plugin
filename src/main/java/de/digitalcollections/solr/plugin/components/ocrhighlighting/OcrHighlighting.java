package de.digitalcollections.solr.plugin.components.ocrhighlighting;

import de.digitalcollections.lucene.analysis.payloads.OcrInfo;
import de.digitalcollections.lucene.analysis.payloads.OcrPayloadHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
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

public class OcrHighlighting extends SearchComponent implements PluginInfoInitialized {

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

  private int coordBits;
  private int wordBits;
  private int lineBits;
  private int pageBits;
  private boolean absoluteCoordinates;

  @Override
  public void prepare(ResponseBuilder rb) {
    // NOP
  }

  @Override
  public void process(ResponseBuilder rb) throws IOException {
    if (rb.req.getParams().getBool("ocr_hl", false)) {
      NamedList<Object> highlighting = doHighlighting(rb.getResults().docList, rb.getQuery(), rb.req);
      rb.rsp.add("ocr_highlighting", highlighting);
    }
  }

  // Adapted from solr's own HighlightComponent
  @Override
  public void modifyRequest(ResponseBuilder rb, SearchComponent who, ShardRequest sreq) {
    if (!(rb.req.getParams().getBool("ocr_hl", false))) {
      return;
    }

    // Turn on highlighting only only when retrieving fields
    if ((sreq.purpose & ShardRequest.PURPOSE_GET_FIELDS) != 0) {
      sreq.purpose |= ShardRequest.PURPOSE_GET_HIGHLIGHTS;
      // should already be true...
      sreq.params.set("ocr_hl", "true");     // TODO: Maybe set hl_params?
    } else {
      sreq.params.set("ocr_hl", "false");
    }
  }

  // Adapted from solr's own HighlightComponent
  @SuppressWarnings("unchecked")
  @Override
  public void finishStage(ResponseBuilder rb) {
    if (!rb.req.getParams().getBool("ocr_hl", false) || rb.stage != ResponseBuilder.STAGE_GET_FIELDS) {
      return;
    }

    NamedList.NamedListEntry[] arr = new NamedList.NamedListEntry[rb.resultIds.size()];
    rb.finished.stream()
            .filter(sreq -> (sreq.purpose & ShardRequest.PURPOSE_GET_HIGHLIGHTS) != 0)
            .flatMap(sreq -> sreq.responses.stream())
            // can't expect the highlight content if there was an exception for this request
            // this should only happen when using shards.tolerant=true
            .filter(resp -> resp.getException() == null)
            .map(resp -> (NamedList) resp.getSolrResponse().getResponse().get("ocr_highlighting"))
            .forEach(hl -> SolrPluginUtils.copyNamedListIntoArrayByDocPosInResponse(hl, rb.resultIds, arr));

    // remove nulls in case not all docs were able to be retrieved
    rb.rsp.add("ocr_highlighting", SolrPluginUtils.removeNulls(arr, new SimpleOrderedMap<>()));
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public void init(PluginInfo info) {
    this.coordBits = Integer.parseInt(info.attributes.getOrDefault("coordinateBits", "12"));
    this.pageBits = Integer.parseInt(info.attributes.getOrDefault("pageBits", "0"));
    this.lineBits = Integer.parseInt(info.attributes.getOrDefault("lineBits", "0"));
    this.wordBits = Integer.parseInt(info.attributes.getOrDefault("wordBits", "0"));
    this.absoluteCoordinates = Boolean.parseBoolean(info.attributes.getOrDefault("absoluteCoordinates", "false"));
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

  /**
   * Generates a list of highlighted query term coordinates for each item in a list of documents, or returns null if highlighting is disabled.
   *
   * @param docs          query results
   * @param query         the query
   * @param req           the current request
   * @return              NamedList containing a {@link NamedList} for each document,
   *                      which in turns contains `({@link String} field, {@link OcrInfo} coordinates)` pairs.
   */
  private NamedList<Object> doHighlighting(DocList docs, Query query, SolrQueryRequest req) throws IOException {
    SolrParams params = req.getParams();
    int maxHighlightsPerDoc = params.getInt("ocr_hl.maxPerDoc", -1);
    int maxHighlightsPerPage = params.getInt("ocr_hl.maxPerPage", -1);
    IndexReader reader = req.getSearcher().getIndexReader();

    int[] docIds = toDocIDs(docs);
    String[] keys = getUniqueKeys(req.getSearcher(), docIds);
    String[] fieldNames = params.getParams("ocr_hl.fields");

    // For each document, obtain a mapping from field names to their matching OCR boxes
    List<Map<String, OcrInfo[]>> boxes = new ArrayList<>();
    for (int docId : docIds) {
      Map<String, OcrInfo[]> docBoxes = new HashMap<>();
      for (String fieldName : fieldNames) {
        // We grab the terms in their UTF-8 encoded form to avoid costly decoding operations
        // when checking for term equality down the line
        Set<BytesRef> termSet = getTerms(query, fieldName);
        OcrInfo[] ocrInfos = getOcrInfos(reader, docId, fieldName, termSet, maxHighlightsPerDoc, maxHighlightsPerPage);
        docBoxes.put(fieldName, ocrInfos);
      }
      boxes.add(docBoxes);
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

  /**
   * Retrieve all {@link OcrInfo}s for matching terms from a given field in a document.
   *
   * This takes a lot of inspiration from the {@link org.apache.lucene.search.uhighlight.UnifiedHighlighter}, thanks
   * to David Smiley (@dsmiley) for pointing out that term vectors are not necessary for this highlighter.
   *
   * @param reader A reader into the search index
   * @param docId Identifier of the matching document
   * @param fieldName Field to obtain OCR information from
   * @param termSet Set of matching terms
   * @param maxHighlightsPerDoc Maximum number of OCR terms per document
   * @param maxHighlightsPerPage Maximum number of OCR terms per page
   * @return All OCR information for matching terms on all positions in the field
   * @throws IOException Error during retrieval from index
   */
  private OcrInfo[] getOcrInfos(IndexReader reader, int docId, String fieldName, Set<BytesRef> termSet,
          int maxHighlightsPerDoc, int maxHighlightsPerPage) throws IOException {
    List<OcrInfo> ocrList = new ArrayList<>();

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
      return new OcrInfo[]{};
    }

    final TermsEnum termsEnum = terms.iterator();
    int currentPage = -1;
    int matchesOnCurrentPage = 0;

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
      for (int i = 0; i < freq && (maxHighlightsPerDoc < 0 || ocrList.size() < maxHighlightsPerDoc); i++) {
        postingsEnum.nextPosition();
        BytesRef payload = postingsEnum.getPayload();
        OcrInfo info = OcrPayloadHelper.decodeOcrInfo(payload, coordBits, wordBits, lineBits, pageBits, absoluteCoordinates);
        if (info.getPageIndex() != currentPage) {  // Are we on a new page?
          matchesOnCurrentPage = 0;
          currentPage = info.getPageIndex();
        }
        if (maxHighlightsPerPage < 0 || matchesOnCurrentPage < maxHighlightsPerPage) {  // Limit matches per page?
          info.setTerm(term.utf8ToString());
          ocrList.add(info);
          matchesOnCurrentPage++;
        }
      }
    }
    return ocrList.stream().sorted().toArray(OcrInfo[]::new);
  }

  private NamedList<Object> encodeOcrInfo(OcrInfo info) {
    NamedList<Object> encoded = new SimpleOrderedMap<>();
    if (info.getPageIndex() >= 0) {
      encoded.add("page", info.getPageIndex());
    }
    if (info.getLineIndex() >= 0) {
      encoded.add("line", info.getLineIndex());
    }
    if (info.getWordIndex() >= 0) {
      encoded.add("word", info.getWordIndex());
    }
    encoded.add("term", info.getTerm());

    if (absoluteCoordinates) {
      encoded.add("x", (int) info.getHorizontalOffset());
      encoded.add("y", (int) info.getVerticalOffset());
      encoded.add("width", (int) info.getWidth());
      encoded.add("height", (int) info.getHeight());
    } else {
      encoded.add("x", info.getHorizontalOffset());
      encoded.add("y", info.getVerticalOffset());
      encoded.add("width", info.getWidth());
      encoded.add("height", info.getHeight());
    }
    return encoded;
  }

  /**
   * Encode the highlighting result into a format that can be used by upstream users.
   */
  private NamedList<Object> encodeSnippets(String[] keys, String[] fieldNames, List<Map<String, OcrInfo[]>> ocrInfos) {
    NamedList<Object> list = new SimpleOrderedMap<>();
    for (int i = 0; i < keys.length; i++) {
      NamedList<Object> summary = new SimpleOrderedMap<>();
      Map<String, OcrInfo[]> docBoxes = ocrInfos.get(i);
      for (String field : fieldNames) {
        summary.add(field,
                Arrays.stream(docBoxes.get(field)).sorted().map(this::encodeOcrInfo).toArray());
      }
      list.add(keys[i], summary);
    }
    return list;
  }
}
