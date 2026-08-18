package com.ata.jobdata.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MockMvc builds requests in-process and never runs Tomcat's query-string parser, so it happily
 * accepts brackets that the real server rejects with 400. This test goes over a real socket, which
 * is the only way to prove {@code ?salary[gte]=120000} works as the brief writes it — unencoded.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BracketFilterSyntaxTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void acceptsUnencodedBracketsInTheQueryString() {
        // URI (not a String) so the brackets reach the server exactly as typed, unencoded
        URI url = URI.create("http://localhost:" + port + "/api/job_data?salary[gte]=120000&size=1");

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"salary\"");
    }
}
