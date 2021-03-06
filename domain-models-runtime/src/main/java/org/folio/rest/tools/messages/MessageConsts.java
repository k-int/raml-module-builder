package org.folio.rest.tools.messages;

public enum MessageConsts implements MessageEnum {

  InitializeVerticleFail("10000"),
  InternalServerError("10001"),
  OperationNotSupported("10002"),
  UnableToProcessRequest("10003"),
  InvalidParameters("10004"),
  HTTPMethodNotSupported("10005"),
  ContentTypeError("10006"),
  AcceptHeaderError("10007"),
  ObjectDoesNotExist("10008"),
  ImportFailed("10009"),
  InvalidURLPath("10010"),
  FileUploadError("10011"),
  NoRecordsUpdated("10012"),
  Timer("10013"),
  DeletedCountError("10014");

  private String code;

  private MessageConsts(String code){
    this.code = code;
  }

  @Override
  public String getCode(){
    return code;
  }

}
