package tech.stackable.hadoop;

import static tech.stackable.hadoop.StackableGroupMapper.OPA_MAPPING_URL_PROP;

import java.net.URI;
import java.net.http.HttpResponse;

public abstract class OpaException extends RuntimeException {

  protected OpaException(String message, Throwable cause) {
    super(message, cause);
  }

  public static final class UriInvalid extends OpaException {
    public UriInvalid(URI uri, Throwable cause) {
      super(
          "Open Policy Agent URI is invalid (see configuration property \""
              + OPA_MAPPING_URL_PROP
              + "\"): "
              + uri,
          cause);
    }
  }

  public static final class EndPointNotFound extends OpaException {
    public EndPointNotFound(String url) {
      super(
          "Open Policy Agent URI is unreachable (see configuration property \""
              + OPA_MAPPING_URL_PROP
              + "\"): "
              + url,
          null);
    }
  }

  public static final class OpaServerError extends OpaException {
    public <T> OpaServerError(String query, HttpResponse<T> response) {
      super(
          "OPA server returned status "
              + response.statusCode()
              + " when processing query "
              + query
              + ": "
              + response.body(),
          null);
    }
  }
}
