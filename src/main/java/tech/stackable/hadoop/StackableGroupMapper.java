package tech.stackable.hadoop;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.GroupMappingServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StackableGroupMapper implements GroupMappingServiceProvider {

  private static final Logger LOG = LoggerFactory.getLogger(StackableGroupMapper.class);
  public static final String OPA_MAPPING_URL_PROP = "hadoop.security.group.mapping.opa.url";
  private static final String OPA_MAPPING_GROUP_NAME_PROP =
      "hadoop.security.group.mapping.opa.list.name";
  // response base field: see https://www.openpolicyagent.org/docs/latest/rest-api/#response-message
  private static final String OPA_RESULT_FIELD = "result";
  private final String mappingGroupName;

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper json;
  private URI opaUri;

  public enum HadoopConfig {
    INSTANCE;
    private final Configuration configuration = new Configuration();

    public Configuration getConfiguration() {
      return this.configuration;
    }
  }

  public StackableGroupMapper() {
    // guaranteed to be only called once (Effective Java: Item 3)
    Configuration configuration = HadoopConfig.INSTANCE.getConfiguration();

    String opaMappingUrl = configuration.get(OPA_MAPPING_URL_PROP);
    if (opaMappingUrl == null) {
      throw new RuntimeException("Config \"" + OPA_MAPPING_URL_PROP + "\" missing");
    }

    try {
      this.opaUri = URI.create(opaMappingUrl);
    } catch (Exception e) {
      throw new OpaException.UriInvalid(opaUri, e);
    }

    this.mappingGroupName = configuration.get(OPA_MAPPING_GROUP_NAME_PROP);
    if (mappingGroupName == null) {
      throw new RuntimeException("Config \"" + OPA_MAPPING_GROUP_NAME_PROP + "\" missing");
    }

    LOG.info("OPA mapping URL [{}]", opaMappingUrl);
    LOG.info("OPA mapping group [{}]", mappingGroupName);

    this.json =
        new ObjectMapper()
            // https://github.com/stackabletech/trino-opa-authorizer/issues/24
            // OPA server can send other fields, such as `decision_id`` when enabling decision logs
            // We could add all the fields we *currently* know, but it's more future-proof to ignore
            // any unknown fields.
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // do not include null values
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  /**
   * Returns list of groups for a user.
   *
   * @param user get groups for this user
   * @return list of groups for a given user
   */
  @Override
  public List<String> getGroups(String user) throws IOException {
    LOG.info("Calling StackableGroupMapper.getGroups for user [{}]", user);

    OpaQuery query = new OpaQuery(new OpaQuery.OpaQueryInput(user));
    String body = json.writeValueAsString(query);

    LOG.debug("Request body [{}]", body);
    HttpResponse<String> response = null;
    try {
      response =
          httpClient.send(
              HttpRequest.newBuilder(opaUri)
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(body))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      LOG.info("Opa response [{}]", response.body());
    } catch (InterruptedException e) {
      LOG.error(e.getMessage());
    }

    switch (Objects.requireNonNull(response).statusCode()) {
      case 200:
        break;
      case 404:
        throw new OpaException.EndPointNotFound(opaUri.toString());
      default:
        throw new OpaException.OpaServerError(query.toString(), response);
    }

    String responseBody = response.body();
    LOG.debug("Response body [{}]", responseBody);

    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>) json.readValue(responseBody, HashMap.class).get(OPA_RESULT_FIELD);
    List<String> groups = (List<String>) result.get(this.mappingGroupName);

    LOG.info("Groups for [{}]: [{}]", user, groups);

    return groups;
  }

  /** Caches groups, no need to do that for this provider */
  @Override
  public void cacheGroupsRefresh() {
    // does nothing in this provider of user to groups mapping
    LOG.info("ignoring cacheGroupsRefresh: caching should be provided by the policy provider");
  }

  /**
   * Adds groups to cache, no need to do that for this provider
   *
   * @param groups unused
   */
  @Override
  public void cacheGroupsAdd(List<String> groups) {
    // does nothing in this provider of user to groups mapping
    LOG.info(
        "ignoring cacheGroupsAdd for groups [{}]: caching should be provided by the policy provider",
        groups);
  }
}