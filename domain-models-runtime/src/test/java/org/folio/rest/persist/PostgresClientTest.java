package org.folio.rest.persist;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.vertx.core.Vertx;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.junit.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class PostgresClientTest {

  private static Vertx vertx;
  private static PostgresClient client;

  @BeforeClass
  public static void before() throws Exception {
    vertx = Vertx.vertx();

    PostgresClient.setIsEmbedded(true);
    client = PostgresClient.getInstance(vertx);
    client.startEmbeddedPostgres();

    initialiseSchema();
  }


  @AfterClass
  public static void after() {
    if(client != null) {
      client.stopEmbeddedPostgres();
    }

    if(vertx != null) {
      vertx.close();
    }
  }

  @Before
  public void beforeEach() throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture recordTableTruncated = new CompletableFuture();

    client.mutate(
      "TRUNCATE TABLE test.record;",
      res -> {
        if(res.succeeded()){
          recordTableTruncated.complete(null);
        }
        else{
          recordTableTruncated.completeExceptionally(res.cause());
        }
      });

    recordTableTruncated.get(5, TimeUnit.SECONDS);
  }

  @Test
  public void canCreateNewRecord() throws Exception {

    String firstId = UUID.randomUUID().toString();
    String secondId = UUID.randomUUID().toString();

    Record firstRecord = new Record(firstId, "Some text");
    Record secondRecord = new Record(secondId, "Some other text");

    saveRecord(firstRecord);
    saveRecord(secondRecord);

    List<Record> findSecondRecordResults = findRecordsById(secondId);
    List<Record> findFirstRecordResults = findRecordsById(firstId);

    assertThat(findSecondRecordResults.size(), equalTo(1));
    assertThat(findFirstRecordResults.size(), equalTo(1));

    assertThat(findSecondRecordResults.get(0).text, equalTo("Some other text"));
    assertThat(findFirstRecordResults.get(0).text, equalTo("Some text"));

    assertThat(findSecondRecordResults.get(0).id, notNullValue());
    assertThat(findFirstRecordResults.get(0).id, notNullValue());
  }

  private List<Record> findRecordsById(String id) throws Exception {

    CompletableFuture<Object[]> foundResult = new CompletableFuture<>();

    Criteria idCriteria = new Criteria();
    idCriteria.addField("'id'");
    idCriteria.setOperation("=");
    idCriteria.setValue(id);
    Criterion criterion = new Criterion(idCriteria);

    client.get(
      "test.record",
      Record.class,
      criterion,
      false,
      reply -> {
        if(reply.succeeded()) {
          foundResult.complete(reply.result());
        }
        else {
          foundResult.completeExceptionally(reply.cause());
        }
      });

    Object[] result = foundResult.get(2, TimeUnit.SECONDS);

    return (List<Record>)result[0];
  }

  private void saveRecord(Record record) throws Exception {
    CompletableFuture<Integer> savedResult = new CompletableFuture<>();

    client.save("test.record", record,
      reply -> {
        if(reply.succeeded()) {
          savedResult.complete(Integer.parseInt(reply.result()));
        }
        else {
          savedResult.completeExceptionally(reply.cause());
        }
      });

    savedResult.get(2, TimeUnit.SECONDS);
  }

  private static void initialiseSchema() throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture schemaInitialissed = new CompletableFuture();

    client.mutate(
      "CREATE SCHEMA test;" +
        "CREATE TABLE test.record (_id SERIAL PRIMARY KEY, jsonb JSONB NOT NULL)",
      res -> {
        if(res.succeeded()){
          schemaInitialissed.complete(null);
        }
        else{
          schemaInitialissed.completeExceptionally(res.cause());
        }
      });

    schemaInitialissed.get(5, TimeUnit.SECONDS);
  }

  @JsonPropertyOrder({
    "id",
    "text"
  })
  public static class Record {

    @JsonProperty("id")
    private String id;

    @JsonProperty("text")
    private String text;

    public Record(
      @JsonProperty("id")String id,
      @JsonProperty("text")String text) {

      this.id = id;
      this.text = text;
    }

    @JsonProperty("id")
    public String getId() {
      return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
      this.id = id;
    }

    @JsonProperty("text")
    public String getText() { return text; }

    @JsonProperty("text")
    public void setText(String text) { this.text = text; }
  }
}


