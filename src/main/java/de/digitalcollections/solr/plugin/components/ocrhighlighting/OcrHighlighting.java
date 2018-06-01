package de.digitalcollections.solr.plugin.components.ocrhighlighting;

import de.digitalcollections.lucene.analysis.payloads.OcrInfo;
import de.digitalcollections.lucene.analysis.payloads.OcrPayloadHelper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
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

public class OcrHighlighting extends SearchComponent implements PluginInfoInitialized {

  private int coordBits;
  private int wordBits;
  private int lineBits;
  private int pageBits;

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
    if (!(rb.req.getParams().getBool("ocr_hl", false))) return;

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

    FieldQueryAdapter fq = new FieldQueryAdapter(query, reader);
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
        Set<BytesRef> termSet = fq.getBytesTermSet(fieldName);
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
    final Fields vectors = reader.getTermVectors(docId);
    if (vectors == null) {
      return new OcrInfo[]{};
    }

    final Terms vector = vectors.terms(fieldName);
    if (vector == null || !vector.hasPositions() || !vector.hasPayloads()) {
      return new OcrInfo[]{};
    }

    final TermsEnum termsEnum = vector.iterator();
    PostingsEnum dpEnum = null;
    BytesRef text;
    int currentPage = -1;
    int matchesOnCurrentPage = 0;

    // TODO: This is currently O(n) in respect to the document vocabulary size.
    //       Unfortunately there's no easy way to avoid a linear scan with TermsEnum :/
    while ((text = termsEnum.next()) != null && (maxHighlightsPerDoc < 0 || ocrList.size() < maxHighlightsPerDoc)) {
      if (!termSet.contains(text)) {
        continue;
      }
      dpEnum = termsEnum.postings(dpEnum, PostingsEnum.POSITIONS | PostingsEnum.PAYLOADS);
      dpEnum.nextDoc();

      final int freq = dpEnum.freq();
      for (int i = 0; i < freq && (maxHighlightsPerDoc < 0 || ocrList.size() < maxHighlightsPerDoc); i++) {
        dpEnum.nextPosition();
        BytesRef payload = dpEnum.getPayload();
        OcrInfo info = OcrPayloadHelper.decodeOcrInfo(payload, coordBits, wordBits, lineBits, pageBits);
        if (info.getPageIndex() != currentPage) {  // Are we on a new page?
          matchesOnCurrentPage = 0;
          currentPage = info.getPageIndex();
        }
        if (maxHighlightsPerPage < 0 || matchesOnCurrentPage < maxHighlightsPerPage) {  // Limit matches per page?
          info.setTerm(text.utf8ToString());
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
    encoded.add("x", info.getHorizontalOffset());
    encoded.add("y", info.getVerticalOffset());
    encoded.add("width", info.getWidth());
    encoded.add("height", info.getHeight());
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
