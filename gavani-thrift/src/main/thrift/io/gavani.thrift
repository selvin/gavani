namespace java io.gavani.thriftjava
#@namespace scala io.gavani.thriftscala

struct StatWriteRequest {
  1: required string statNamespace
  2: required string statSource
  3: required i32 timestamp
  4: required map<string, double> statRecords
}

struct StatReadRequest {
  1: required string statNamespace
  2: required string statSource
  3: required string statName
  4: required i32 fromTimestamp       // inclusive
  5: required i32 toTimestamp         // exclusive
  6: required i32 timestampIncrement  // 60, 3600, 86400
}

struct StatReadResponse {
  1: required list<double> values
  2: required list<i32> failedTimestamps
}

struct StatBrowseRequest {
  1: string query
  2: i32 pageNum  // 0-base indexing
  3: i32 pageSize
}

struct StatBrowseResponse {
  1: list<string> names
  2: i32 pageNum
  3: i32 pageSize
  4: i32 total
}

service StatWriter {
  map<string, string> write(StatWriteRequest statWriteRequest)  // returns failing statKey with errorName
}

service StatReader {
   StatReadResponse read(StatReadRequest statReadRequest)
}

service StatBrowser {
  StatBrowseResponse getNamespaces(StatBrowseRequest statBrowseRequest)
  StatBrowseResponse getStatSources(StatBrowseRequest statBrowseRequest)
  StatBrowseResponse getStatNames(StatBrowseRequest statBrowseRequest)
}