/*
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

package org.apache.solr.client.solrj.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;

import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;

/**
*  Queries a Solr instance, and maps SolrDocs to a Stream of Tuples.
**/

public class SolrStream extends TupleStream {

  private static final long serialVersionUID = 1;

  private String baseUrl;
  private Map params;
  private int numWorkers;
  private int workerID;
  private Map<String, String> fieldMappings;
  private transient JSONTupleStream jsonTupleStream;
  private transient HttpSolrClient client;
  private transient SolrClientCache cache;

  public SolrStream(String baseUrl, Map params) {
    this.baseUrl = baseUrl;
    this.params = params;
  }

  public void setFieldMappings(Map<String, String> fieldMappings) {
    this.fieldMappings = fieldMappings;
  }

  public List<TupleStream> children() {
    return new ArrayList();
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setStreamContext(StreamContext context) {
    this.numWorkers = context.numWorkers;
    this.workerID = context.workerID;
    this.cache = context.clientCache;
  }

  /**
  * Opens the stream to a single Solr instance.
  **/

  public void open() throws IOException {

    if(cache == null) {
      client = new HttpSolrClient(baseUrl);
    } else {
      client = cache.getHttpSolrClient(baseUrl);
    }

    try {
      jsonTupleStream = JSONTupleStream.create(client, loadParams(params));
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  private SolrParams loadParams(Map params) {
    ModifiableSolrParams solrParams = new ModifiableSolrParams();
    if(this.numWorkers > 0) {
      String partitionFilter = getPartitionFilter();
      solrParams.add("fq", partitionFilter);
    }

    Iterator<Map.Entry> it = params.entrySet().iterator();
    while(it.hasNext()) {
      Map.Entry entry = it.next();
      solrParams.add((String)entry.getKey(), entry.getValue().toString());
    }

    return solrParams;
  }

  private String getPartitionFilter() {
    StringBuilder buf = new StringBuilder("{!hash workers=");
    buf.append(this.numWorkers);
    buf.append(" worker=");
    buf.append(this.workerID);
    buf.append("}");
    return buf.toString();
  }

  /**
  *  Closes the Stream to a single Solr Instance
  * */

  public void close() throws IOException {
    jsonTupleStream.close();
    if(cache == null) {
      client.close();
    }
  }

  /**
  * Reads a Tuple from the stream. The Stream is completed when Tuple.EOF == true.
  **/

  public Tuple read() throws IOException {
    Map fields = jsonTupleStream.next();
    if(fields == null) {
      //Return the EOF tuple.
      Map m = new HashMap();
      m.put("EOF", true);
      return new Tuple(m);
    } else {
      if(fieldMappings != null) {
        fields = mapFields(fields, fieldMappings);
      }
      return new Tuple(fields);
    }
  }

  private Map mapFields(Map fields, Map<String,String> mappings) {

    Iterator<Map.Entry<String,String>> it = mappings.entrySet().iterator();
    while(it.hasNext()) {
      Map.Entry<String,String> entry = it.next();
      String mapFrom = entry.getKey();
      String mapTo = entry.getValue();
      Object o = fields.get(mapFrom);
      fields.remove(mapFrom);
      fields.put(mapTo, o);
    }

    return fields;
  }
}