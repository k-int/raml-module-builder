package org.folio.rest.persist;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.rest.tools.utils.NetworkUtils;

import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.DownloadConfigBuilder;
import de.flapdoodle.embed.mongo.config.ExtractedArtifactStoreBuilder;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import de.flapdoodle.embed.process.extract.UserTempNaming;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;

public class MongoCRUD {

  private MongoClient               client;

  private static MongoCRUD          instance;

  public static final String        JSON_PROP_COLLECTION    = "collection";
  public static final String        JSON_PROP_AUTHORIZATION = "authorization";
  public static final String        JSON_PROP_QUERY         = "query";
  public static final String        JSON_PROP_ORDERBY       = "orderBy";
  public static final String        JSON_PROP_OFFSET        = "offset";
  public static final String        JSON_PROP_LIMIT         = "limit";
  public static final String        JSON_PROP_ENTITY_ID     = "entityId";
  public static final String        JSON_PROP_OPS           = "_op";
  public static final String        JSON_PROP_ORDER         = "order";
  public static final String        JSON_PROP_ENTITY        = "entity";
  public static final String        JSON_PROP_SORT          = "sort";
  public static final String        JSON_PROP_CLASS         = "clazz";
  public static final MongodStarter MONGODB                 = getMongodStarter();
  private static MongodProcess mongoProcess;
  
  public static String MONGO_HOST = null;
  public static int MONGO_PORT = 27017;

  private static ObjectMapper mapper = new ObjectMapper();

  private static boolean embeddedMode = false;

  private static String configPath;

  private static final Logger log = LoggerFactory.getLogger("MongoCRUD");

  
  private MongoCRUD(Vertx vertx) throws Exception {
    init(vertx);
  }
  
  /**
   * must be called before getInstance() for this to take affect
   * @param embed - whether to use an embedded mongo instance
   */
  public static void setIsEmbedded(boolean embed){
    embeddedMode = embed;
  }
  
  public static boolean isEmbedded(){
    return embeddedMode;
  }
  
  /**
   * must be called before getInstance() for this to take affect
   * @param path
   */
  public static void setConfigFilePath(String path){
    configPath = path;
  }
  
  // will return null on exception
  public static MongoCRUD getInstance(Vertx vertx) {
    // assumes a single thread vertx model so no sync needed
    if (instance == null) {
      try {
        instance = new MongoCRUD(vertx);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return instance;
  }

  /**
   * Return a MongodStarter with UserTempNaming() to avoid windows firewall dialog popups.
   *
   * Code taken from
   * https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo/blob/d67667f/README.md#usage---custom-mongod-filename
   *
   * @return the MongodStarter
   */
  private static final MongodStarter getMongodStarter() {
    Command command = Command.MongoD;

    IRuntimeConfig runtimeConfig = new RuntimeConfigBuilder()
      .defaults(command)
      .artifactStore(new ExtractedArtifactStoreBuilder()
        .defaults(command)
        .download(new DownloadConfigBuilder()
          .defaultsForCommand(command).build())
        .executableNaming(new UserTempNaming()))
      .build();

    return MongodStarter.getInstance(runtimeConfig);
  }

  private MongoClient init(Vertx vertx) throws Exception {
    
    if (embeddedMode) {
      MONGO_HOST = "localhost";
      MONGO_PORT = NetworkUtils.nextFreePort();
      JsonObject jsonConf = new JsonObject();
      jsonConf.put("db_name", "indexd_test");
      jsonConf.put("host", MONGO_HOST);
      jsonConf.put("port", MONGO_PORT);
      client = MongoClient.createShared(vertx, jsonConf);
      log.info("created embedded mongo config on port " + MONGO_PORT);
    } else {      
      String path = "/mongo-conf.json";
      if(configPath != null){
        path = configPath;
        System.out.println("Loading mongo-conf.json from " + configPath);
      }
      else{
        System.out.println("Loading mongo-conf.json from default " + path);
      }
      JsonObject jsonConf = new LoadConfs().loadConfig(path);
      if(jsonConf == null){        
        JsonObject example = new JsonObject(" {  \"db_name\": \"indexd_test\",  \"host\" : \"SERVER_NAME\",\"port\" : 1234,"+
        "\"maxPoolSize\" : 3, \"minPoolSize\" : 1, \"maxIdleTimeMS\" : 300000,\"maxLifeTimeMS\" : 3600000,"+      
        "\"waitQueueMultiple\"  : 100, \"waitQueueTimeoutMS\" : 10000,\"maintenanceFrequencyMS\" : 2000, "+ 
        "\"maintenanceInitialDelayMS\" : 500,\"connectTimeoutMS\" : 300000, \"socketTimeoutMS\"  : 100000,"+
        "\"sendBufferSize\" : 8192, \"receiveBufferSize\" : 8192, \"keepAlive\" : true}");
        //not in embedded mode but there is no conf file found
        throw new Exception("\nNo mongo-conf.json file found at "+path+
          ". You need to run with either \n"
          + "1. embed_mongo=true \n"
          + "2. mongo_connection=<path_to_mongo-conf.json>\n"
          + "3. place mongo-conf.json in the resources dir\n"
          + " can not connect to any db store\n"
          + "EXAMPLE FILE:\n"
          + example.encodePrettily());
      }
      else{
        MONGO_HOST = jsonConf.getString("host");
        MONGO_PORT = jsonConf.getInteger("port");
        client = MongoClient.createShared(vertx, jsonConf, "MongoConnectionPool");  
      }
    }
    return client;
  }

  public void save(String collection, Object entity, Handler<AsyncResult<String>> replyHandler) {
    
    long start = System.nanoTime();

    String obj = entity2String(entity);
    try {
      client.save(collection , new JsonObject(obj), res1 -> {
        if (res1.succeeded()) {
          String id = res1.result();
          System.out.println("Saved item with id " + id);
          replyHandler.handle(io.vertx.core.Future.succeededFuture(id));

        } else {
          res1.cause().printStackTrace();
          replyHandler.handle(io.vertx.core.Future.failedFuture(res1.cause().toString()));
        }
        if(log.isDebugEnabled()){
          elapsedTime("save() " + collection, start);
        }
      });
    } catch (Throwable e) {
      e.printStackTrace();
      replyHandler.handle(io.vertx.core.Future.failedFuture(e.getLocalizedMessage()));
    }
  }
  
  public void delete(String collection, String id, Handler<AsyncResult<Void>> replyHandler) {
    long start = System.nanoTime();

    try {
      JsonObject jobj = new JsonObject();
      jobj.put("_id", id);
      client.removeDocument(collection, jobj, res -> {
        if (res.succeeded()) {
          System.out.println("deleted item");
          replyHandler.handle(io.vertx.core.Future.succeededFuture());
        } else {
          res.cause().printStackTrace();
          replyHandler.handle(io.vertx.core.Future.failedFuture(res.cause().toString()));
        }
        if(log.isDebugEnabled()){
          elapsedTime("delete() " + collection + " " + jobj.encode(), start);
        }
      });
    } catch (Throwable e) {
      e.printStackTrace();
      replyHandler.handle(io.vertx.core.Future.failedFuture(e.getLocalizedMessage()));

    }
  }

  public void delete(String collection, JsonObject query, Handler<AsyncResult<Void>> replyHandler) {
    long start = System.nanoTime();

    try {
      client.removeDocument(collection, query, res -> {
        if (res.succeeded()) {
          System.out.println("deleted item");
          replyHandler.handle(io.vertx.core.Future.succeededFuture());
        } else {
          res.cause().printStackTrace();
          replyHandler.handle(io.vertx.core.Future.failedFuture(res.cause().toString()));
        }
        if(log.isDebugEnabled()){
          elapsedTime("delete() " + collection + " " + query.encode(), start);
        }
      });
    } catch (Throwable e) {
      e.printStackTrace();
      replyHandler.handle(io.vertx.core.Future.failedFuture(e.getLocalizedMessage()));

    }
  }
  
  public void get(JsonObject json, Handler<AsyncResult<List<?>>> replyHandler) {

    long start = System.nanoTime();
    
    try {
      String clazz = json.getString(JSON_PROP_CLASS);
      Integer limit = json.getInteger(JSON_PROP_LIMIT);
      Integer offset = json.getInteger(JSON_PROP_OFFSET);
      final String collection = json.getString(JSON_PROP_COLLECTION);
      final JsonObject query = new JsonObject(); 
      FindOptions fo = new FindOptions();
      
      Class<?> cls = Class.forName(clazz);
      
      if(limit != null){
        fo.setLimit(limit); 
      }
      if(offset != null){
        fo.setSkip(offset);
      }
      if(json.getJsonObject(JSON_PROP_QUERY) != null){
        query.mergeIn(json.getJsonObject(JSON_PROP_QUERY));
      }
      JsonObject sort = json.getJsonObject(JSON_PROP_SORT);
      if(sort != null){
        fo.setSort(sort); 
      }      
      client.findWithOptions(collection, query, fo, res -> {
        if (res.succeeded()) {
          List<JsonObject> reply = null;
          try {
            reply = mapper.readValue(res.result().toString(),
              mapper.getTypeFactory().constructCollectionType(List.class, cls));
            if(reply == null){
              replyHandler.handle(io.vertx.core.Future.failedFuture("Seems like a mapping issue between requested objects and returned objects"));
            }else{
              replyHandler.handle(io.vertx.core.Future.succeededFuture(reply));
            }
          } catch (Exception e) {
            e.printStackTrace();
            replyHandler.handle(io.vertx.core.Future.failedFuture(e.getMessage()));
          }
        } else {
          res.cause().printStackTrace();
          replyHandler.handle(io.vertx.core.Future.failedFuture(res.cause().toString()));
        }
        if(log.isDebugEnabled()){
          elapsedTime("get() " + collection + " " + query.encode(), start);
        }
      });
    } catch (Throwable e) {
      e.printStackTrace();
      replyHandler.handle(io.vertx.core.Future.failedFuture(e.getLocalizedMessage()));
    }
  }
  
  public void update(String collection, Object entity, JsonObject query,  boolean addUpdateDate, Handler<AsyncResult<Void>> replyHandler) {

    update(collection, entity, query, false, addUpdateDate, replyHandler);
  }
  
  public void update(String collection, Object entity, JsonObject query, Handler<AsyncResult<Void>> replyHandler) {

    update(collection, entity, query, false, false, replyHandler);
  }
  
  public void update(String collection, Object entity, JsonObject query,  boolean upsert, boolean addUpdateDate, Handler<AsyncResult<Void>> replyHandler) {

    long start = System.nanoTime();
    
    JsonObject ret = new JsonObject();
    try {
      UpdateOptions options = new UpdateOptions().setUpsert(upsert);
      JsonObject update = new JsonObject();
      update.put("$set", new JsonObject(entity2String(entity)));
      
      if(addUpdateDate){
        update.put("$currentDate", new JsonObject("{\"last_modified\": true}"));  
      }
      if (entity == null){ 
        ret.put("error", "entity is null");
      }
      if(query == null) {
        ret.put("error", "query is null");
      }

      client.updateCollectionWithOptions(collection, query, update, options, res -> {

        if (res.succeeded()) {
          System.out.println(" replaced !");
          replyHandler.handle(io.vertx.core.Future.succeededFuture());
        } else {
          res.cause().printStackTrace();
          replyHandler.handle(io.vertx.core.Future.failedFuture(res.cause().toString()));
        }
        if(log.isDebugEnabled()){
          elapsedTime("update() " + collection + " " + query.encode(), start);
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      replyHandler.handle(io.vertx.core.Future.failedFuture(e.getLocalizedMessage()));

    }
  } 
  
  public void addToArray(String collection, String arrayName, Object arrayEntry, JsonObject query, Handler<AsyncResult<Void>> replyHandler) {

    long start = System.nanoTime();
    
    JsonObject ret = new JsonObject();
    try {
      UpdateOptions options = new UpdateOptions();
      JsonObject update = new JsonObject();
      JsonObject array = new JsonObject();

      if (arrayEntry == null){ 
        ret.put("error", "arrayEntry is null");
        replyHandler.handle(io.vertx.core.Future.failedFuture("arrayEntry to update is null"));
      }
      else if(query == null) {
        ret.put("error", "query is null");
        replyHandler.handle(io.vertx.core.Future.failedFuture("query to update is null"));
      }
      else{
        if(((List)arrayEntry).size() == 1){
          array.put(arrayName, new JsonObject(entity2String(((List)arrayEntry).get(0))));
        }
        else{
          JsonObject each = new JsonObject();
          each.put("$each", new JsonArray( entity2String(arrayEntry)));
          array.put(arrayName,  each );
        }
        
        update.put("$push", array);
        
        client.updateCollectionWithOptions(collection, query, update, options, res -> {

          if (res.succeeded()) {
            System.out.println(" replaced !");
            replyHandler.handle(io.vertx.core.Future.succeededFuture());
          } else {
            res.cause().printStackTrace();
            replyHandler.handle(io.vertx.core.Future.failedFuture(res.cause().toString()));
          }
          if(log.isDebugEnabled()){
            elapsedTime("addToArray() " + collection + " " + query.encode(), start);
          }
        });
      }
    } catch (Exception e) {
      e.printStackTrace();
      replyHandler.handle(io.vertx.core.Future.failedFuture(e.getLocalizedMessage()));

    }
  }  
  
  public void startEmbeddedMongo() throws Exception {

    if(mongoProcess == null || !mongoProcess.isProcessRunning()){
      IMongodConfig mongodConfig = new MongodConfigBuilder().version(Version.Main.PRODUCTION).net(new Net(MONGO_PORT, Network.localhostIsIPv6()))
          .build();
      mongoProcess = MONGODB.prepare(mongodConfig).start();
    }
    else{
      log.info("Embedded Mongo on port " + MONGO_PORT + " is already running");
    }
  }

  public static void stopEmbeddedMongo() {
    if (mongoProcess != null) {
      mongoProcess.stop();
    }
  }
  
  private String entity2String(Object entity){
    String obj = null;
    if(entity != null){
      if(entity instanceof JsonObject){
        //json object
        obj = ((JsonObject) entity).encode();
      }
      else if(entity instanceof List<?>){
        obj =  new JsonArray((List)entity).encode();
      }
      else{
        try {
          //pojo
          obj = mapper.writeValueAsString(entity);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        } 
      }
    }  
    return obj;
  }
  
  public static JsonObject buildJson(String returnClazz, String collection, String query, String orderBy, Object order, int offset, int limit){
    JsonObject q = null;
    if(query != null){
      try {
        q = new JsonObject(query);
      } catch (Exception e) {
        e.printStackTrace();
        return null;
      }
    }
    return buildJson(returnClazz, collection, q, orderBy, order, offset, limit );
  }
  
  /**
   * 
   * @param returnClazz - class of objects expected to be returned - for example passing a Fines class to get()
   * fine objects
   * @param collection - the collection to query from
   * @param query - a valid mongodb json query
   * @param orderBy
   * @param order
   * @param offset
   * @param limit
   * @return
   */
  public static JsonObject buildJson(String returnClazz, String collection, JsonObject query, String orderBy, Object order, int offset, int limit){
    try {
      JsonObject req = new JsonObject();
      if(query != null){
        try {
          req.put(MongoCRUD.JSON_PROP_QUERY, query);
        } catch (Exception e) {
          log.error( "Unable to parse query param to json " + e.getLocalizedMessage() );
        }
      }
      
      req.put(MongoCRUD.JSON_PROP_COLLECTION, collection);
      if(offset != -1){
        req.put(MongoCRUD.JSON_PROP_OFFSET, offset);
      }
      if(limit != -1){
        req.put(MongoCRUD.JSON_PROP_LIMIT, limit);
      }    
      req.put(MongoCRUD.JSON_PROP_CLASS, returnClazz);
      if(orderBy != null){
        int ord = -1;
        if("asc".equals(order.toString())){
          ord = 1;
        }
        req.put(MongoCRUD.JSON_PROP_SORT, new JsonObject("{\""+orderBy+"\" : "+ord+"}"));
      }
      return req;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
  
  public static JsonObject buildJson(String returnClazz, String collection, String query){
    return buildJson(returnClazz, collection, query, null, null, -1 ,-1);
  }
  
  public static JsonObject buildJson(String returnClazz, String collection, JsonObject query){
    return buildJson(returnClazz, collection, query, null, null, -1 ,-1);
  }
  
  private void elapsedTime(String info, long start){
      log.debug(new StringBuffer(info).append(" ").append(((System.nanoTime() - start)/1000000)).append(" ms"));
  }
  
}