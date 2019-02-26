/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.examples;

import javax.sql.DataSource;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sql.SqlComponent;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.spi.ComponentCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class CamelConfiguration extends RouteBuilder {

  private static final Logger log = LoggerFactory.getLogger(CamelConfiguration.class);

  @Bean
  ComponentCustomizer<SqlComponent> sqlComponentCustomizer(DataSource dataSource) {
    return (SqlComponent component) -> {
      component.setDataSource(dataSource);
    };
  }
  
  @Override
  public void configure() throws Exception {
    
    rest("/randnum")
      .get("/")
        .produces("application/json")
        .to("direct:fetchRandNums")
      .get("/{accountNum}")
        .produces("application/json")
        .to("direct:fetchRandNumByAccountNum")
      .post("/upload")
        .consumes("multipart/form-data")
        .produces("text/plain")
        .bindingMode(RestBindingMode.off)
        .to("direct:flrDataUpload")
    ;
    
    from("direct:fetchRandNums")
      .log(LoggingLevel.DEBUG, log, "Fetching all randNums")
      .to("sql:SELECT * FROM CDP_RAND_NUM")
      .transform().groovy("request.body.collect({ ['accountNum': it['ACCOUNT_NUM'], 'randNum': it['RAND_NUM']] })")
      .marshal().json(JsonLibrary.Jackson)
    ;
    
    from("direct:fetchRandNumByAccountNum")
      .log(LoggingLevel.DEBUG, log, "Fetching randNum for accountNum: [${header.accountNum}]")
      .to("sql:SELECT RAND_NUM FROM CDP_RAND_NUM WHERE ACCOUNT_NUM=:#${header.accountNum}?outputType=SelectOne")
      .transform().groovy("['randNum': request.body]")
      .marshal().json(JsonLibrary.Jackson)
    ;
    
    from("direct:flrDataUpload")
      .onException(Exception.class)
        .handled(true)
        .log(LoggingLevel.DEBUG, log, "Error inserting record for accountNum=[${body?.get('accountNum')}], error=[${exception}]")
        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
        .setBody(simple("Not OK\n${exception}"))
      .end()
      .log(LoggingLevel.DEBUG, log, "Processing bulk FLR upload")
      .unmarshal().mimeMultipart()
      .convertBodyTo(byte[].class)
      .unmarshal().beanio("/beanio-mappings.xml", "randNumFlrFile", "UTF-8")
      .log(LoggingLevel.DEBUG, log, "Inserting [${body[0].get('entries').size()}] records...")
      .split(simple("${body[0].get('entries')}"))
        .parallelProcessing()
        .to("sql:INSERT INTO CDP_RAND_NUM VALUES (:#${body['accountNum'].trim()},:#${body['randNum']}) ON DUPLICATE KEY UPDATE RAND_NUM=:#${body['randNum']}")
      .end()
      .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
      .setBody(constant("OK"))
    ;
  }
}
