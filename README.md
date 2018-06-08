# Solr OCR Coordinate Payload Plugin
*Efficient indexing and bounding-box "highlighting" for OCR text*


[![Javadocs](http://javadoc.io/badge/de.digitalcollections.search/solr-ocrpayload-plugin.svg)](http://javadoc.io/doc/de.digitalcollections.search/solr-ocrpayload-plugin)
[![Build Status](https://travis-ci.org/dbmdz/solr-ocrpayload-plugin.svg?branch=master)](https://travis-ci.org/dbmdz/solr-ocrpayload-plugin)
[![codecov](https://codecov.io/gh/dbmdz/solr-ocrpayload-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/dbmdz/solr-ocrpayload-plugin)
[![MIT License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![GitHub release](https://img.shields.io/github/release/dbmdz/solr-ocrpayload-plugin.svg?maxAge=2592000)](https://github.com/dbmdz/solr-ocrpayload-plugin/releases)
[![Maven Central](https://img.shields.io/maven-central/v/de.digitalcollections.search/solr-ocrpayload-plugin.svg?maxAge=2592000)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22solr-ocrpayload-plugin%22)

## tl;dr

- Store OCR bounding box information and token position directly in the Solr index in a space-efficient manner
- Retrieve bounding box and token position directly in your Solr query results, no additional parsing necessary

**Indexing**:

The OCR information is appended after each token as a concatenated list of `<key><val>` pairs, see further down
for a detailed description of available keys.

`POST /solr/mycore/update`

```json
[{ "id": "test_document",
   "ocr_text": "this|p13l5n6x111y222w333h444 is|p13l5n7x222y333w444h555 a|p13l5n8x333y333w444h555 test|p13l5n9x444y333w444h555" }]
```

**Querying**:

The plugin adds a new top-level key (`ocr_highlight` in this case) that contains the OCR information for
each matching token as a structured object.

`GET /solr/mycore/select?ocr_hl=true&ocr_hl.fields=ocr_text&indent=true&wt=json&q=test`

```json
{
  "responseHeader": "...",
  "response": {
    "numFound": 1,
    "docs": [{"id": "test_document"}]
  },
  "ocr_highlight":{
    "test_document":{
      "ocr_text":[{
          "term":"test",
          "page":13,
          "line": 5,
          "word": 9,
          "x":0.444,
          "y":0.333,
          "width":0.444,
          "height":0.555}]
    }
  }
}
```

## Use Case
At the Bavarian State Library, we try to provide full-text search over all of our OCRed content. In addition
to obtaining matching documents, the user should also get a small snippet of the corresponding part of the
page image, with the matching words highlighted, similar to what e.g. Google Books provides.


## Approaches
For this to work, we need some way of mapping matching tokens to their corresponding location in the underlying
OCR text. A common approach used by a number of libraries is to **use a secondary microservice for this** that takes
as input a document identifier and a text snippet and will return all coordinates of matching text snippets on
the page. While this approach generally works okay, it has several drawbacks:

- **Performance:** Every snippet requires a query to the OCR service, which itself has to do a linear scan
  through the OCR document. For e.g. a result set of 100 snippets, this will result in 101 queries (initial
  Solr query and 100 snippet queries). Of course this can be optimized by batching and having a good index
  structure for the coordinate lookup, but it's still less than ideal.
- **Storage:** To reliably be able to map text matches to the base text, you have to store a copy of the
  full text in the index, alongside the regular index. This blows up the index size significantly.
  Foregoing storing the text and only using the normalized terms from the index for matching will
  break the mapping to OCR, since depending on the analyzer configuration, Lucene will perform stemming, etc.
  
Alternatively, you could also **store the coordinates directly as strings in the index**. This works by e.g.
indexing each token as `<token>|<coordinates>` and telling Lucene to ignore everything after the pipe during
analysis. As the full text of the document is stored, you wil get back a series of these annotated tokens
as query results and can then parse the coordinates from your highlighting information. This solves the
*Performance* part of the above approach, but worsens the *Storage* problem: For every token, we now not only
have to store the token itself, but an expensive coordinate string as well.

## Our Approach

This plugin uses a similar approach to the above, but solves the *Storage* problem by using an efficient binary
format to store the OCR coordinate information in the index: We use bit-packing to combine a number of OCR
coordinate parameters into a **byte payload**, which is not stored in the field itself, but as an associated
[Lucene Payload](https://lucidworks.com/2017/09/14/solr-payloads/):

- `x`, `y`, `w`, `h`: **Relative** coordinates of the bounding box on the page as floating point values between 0 and 1
- `pageIndex`: Unsigned integer that stores the page index of a token (optional)
- `lineIndex`: Unsigned integer that stores the line index of a token (optional)
- `wordIndex`: Unsigned integer that stores the word index of a token (optional)

For each of these values, you can configure the number of bits the plugin should use to store them, or disable
certain parameters entirely. This allows you to fine-tune the settings to your needs. In our case, for example, we
use these values: `4 * 12 bits (coordinates) + 9 bits (word index) + 11 bits (line index) + 12 bits (page index)`,
resulting in a 80 bit or 10 byte payload per token. A comparable string representation `p0l0n0x000y000w000h000`
would have at least 22 bytes, so we save >50% for every token.

At query time, we then retrieve the payload for each matching token and put the decoded information into the
`ocr_highlight` result key that can be directly used without having to do any additional parsing.

## Usage
### Installation

Download the [latest release from GitHub](https://github.com/dbmdz/solr-ocrpayload-plugin/releases) and put the JAR into your `$SOLR_HOME/$SOLR_CORE/lib/` directory.

### Indexing configuration

To use it, first add the `DelimitedOcrInfoPayloadTokenFilterFactory`☕ filter to your analyzer chain (e.g. for a `ocr_text` field type):

```xml
<fieldtype name="text_ocr" class="solr.TextField"
           termVectors="true" termPositions="true" termPayloads="true">
  <analyzer>
    <tokenizer class="solr.WhitespaceTokenizerFactory"/>
    <filter class="de.digitalcollections.lucene.analysis.util.DelimitedOcrInfoPayloadTokenFilterFactory"
            delimiter="☞" coordinateBits="10" wordBits="0" lineBits="0" pageBits="12" />
    <filter class="solr.StandardFilterFactory"/>
    <filter class="solr.LowerCaseFilterFactory"/>
    <filter class="solr.StopFilterFactory"/>
    <filter class="solr.PorterStemFilterFactory"/>
  </analyzer>
</fieldtype>
```

The filter takes five parameters:

- `delimiter`: Character used for delimiting the payload from the token in the input document (default: `|`)
- `coordinateBits`:  Number of bits to use for encoding OCR coordinates in the index. (mandatory)<br/>
   A value of `10` (default) is recommended, resulting in coordBits to approximately two decimal places.
- `wordBits`: Number of bits to use for encoding the word index.<br/>
   Set to 0 (default) to disable storage of the word index.
- `lineBits`: Number of bits to use for encoding the line index.<br/>
   Set to 0 (default) to disable storage of the line index.
- `pageBits`: Number of bits to use for encoding the page index.<br/>
   Set to 0 (default) to disable storage of the page index.
               
The filter expects the input text to have the coordinates encoded as floating point values between
0 and 1 and with the leading `0.` discarded and laid out as follows (values in brackets are optional):

`<token><delimiter>[p<pageIdx>][l<lineIdx>][n<wordIdx>]x<x>y<y>w<w>h<h>`

As an example, consider the token `foobar` with an OCR box of `(0.50712, 0.31432, 0.87148, 0.05089)`,
the configured delimiter `☞` and storage of indices for the word (`30`), line (`12`) and page (`13`):
`foobar☞p13l12n30x507y314w871h051`.

Finally, you just have to configure your schema to use the field type defined above. Storing the content is **not**
recommended, since it significantly increases the index size and is not used at all for querying and highlighting:

```xml
<field name="ocr_text" type="text_ocr" indexed="true" stored="false" />
```

### Highlighting configuration

To enable highlighting using the OCR payloads, add the `OcrHighlighting` component to your Solr
configuration, configure it with the same `coordinateBits`, `wordBits`, `lineBits` and `pageBits` values
that were used for the filter in the analyzer chain:

```xml
<config>
  <searchComponent name="ocr_highlight"
                   class="de.digitalcollections.solr.plugin.components.ocrhighlighting.OcrHighlighting"
                   coordinateBits="10" wordBits="0" lineBits="0" pageBits="12" />
                   
  <requestHandler name="standard" class="solr.StandardRequestHandler">
    <arr name="last-components">
      <str>ocr_highlight</str>
    </arr>
  </requestHandler>
</config>
```

Now at query time, you can just set the `ocr_hl=true` parameter, specify the fields you want highlighted via
`ocr_hl.fields=myfield,myotherfield` and retrieve highlighted matches with their OCR coordinates:

`GET /solr/mycore/select?ocr_hl=true&ocr_hl.fields=ocr_text&indent=true&q=augsburg&wt=json`

```json
{
  "responseHeader":{
    "status":0,
    "QTime":158},
  "response":{"numFound":526,"start":0,"docs":[
      {
        "id":"bsb10502835"},
      {
        "id":"bsb11032147"},
      {
        "id":"bsb10485243"},
      ...
  },
  "ocr_highlight":{
    "bsb10502835":{
      "ocr_text":[{
          "page":7,
          "position":9,
          "term":"augsburg",
          "x":0.111,
          "y":0.062,
          "width":0.075,
          "height":0.013},
        {
          "page":7,
          "position":264,
          "term":"augsburg",
          "x":0.320,
          "y":0.670,
          "width":0.099,
          "height":0.012},
        ...]}},
       ...
    }
  }
}
```


## FAQ

- **How does highlighting work with phrase queries?**
  
  You will receive a bounding box object for every individual matching term in the phrase.

- **What are the performance and storage implications of using this plugin?**

  *Performance*: With an Intel Xeon E5-1620@3.5GHz on a single core, we measured (with JMH):
  
  - Encoding the Payload: 1,484,443.200 Payloads/Second or ~14.2MiB/s with an 80bit payload
  - Decoding the Payload: 1,593,036.372 Payloads/Second or ~15.2MiB/s with an 80bit payload
  
  *Storage*: This depends on your configuration. With our sample configuration of an 80 bit payload
  (see above), the payload overhead is 10 bytes per token. That is, for a corpus size of 10 Million Tokens,
  you will need approximately 95MiB to store the payloads.
  The actual storage required might be lower, since Lucene compresses the payloads with LZ4.
  
- **Does this work with SolrCloud?**

  It does! We're running it with SolrCloud ourselves.