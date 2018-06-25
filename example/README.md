# Example Setup

## Configuration
The index is configured to store the OCR bounding boxes with the following parameters:

- Coordinates are stored as **absolute pixel values**
- The payload needs **11 bytes** per token
  (14 bits per coordinate, 12 bit for word-, 11 bit for line- and 9 bit for word-indices)


## Dataset
This demo creates an index of the [Google 1000 Books ICDAR 2007 dataset](http://commondatastorage.googleapis.com/books/icdar2007/README.txt).
It consists of 103,672,774 tokens split across 1000 OCRed books taken from the Google Books project.
The resulting index is 2.03GiB in size, compared to 4.4GiB for the uncompressed input documents.


## Running the demo
- Launch the Docker container: `docker-compose up`
- Index the pre-converted OCR volumes with `./index_google1000`
- **Search!** `curl http://localhost:8983/solr/ocrtest/t/select?q=ocr_text:harvard&ocr_hl=true&ocr_hl.fields=ocr_text`


## Converting the hOCR to the input format manually
The instructions above fetch an archive with the hOCRs from the dataset pre-converted
(https://zvdd-ng.de/files/google1000_solr.tgz). If you want to do this yourself, follow these steps:

- Obtain the dataset by downloading the individual books, ideally with a newer version of bash or zsh:
  ```sh
  $ wget http://commondatastorage.googleapis.com/books/icdar2007/Volume_{0000..0999}.zip
  $ for zip in *.zip; do unzip $i; done
  ```
- Convert the individual hOCR files to the format needed by the Solr configuration
  (`<word>☛p:<pageNo>,l:<lineNo>,n:<wordNo>,x:<xOffset>,y:<yOffset>,w:<width>,h:<height>`):
  ```sh
  $ for hocr in Volume_*/hOCR.html; do ./hocr2solr $hocr > $(echo $hocr sed 's/.html/.txt'); done
  ```
- Index the books by passing the directory with the `.txt`-files as the first parameter:
  ```sh
  $ ./index_google1000 <txt-dir>
  ```
