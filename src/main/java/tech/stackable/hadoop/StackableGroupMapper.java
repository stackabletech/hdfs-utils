package tech.stackable.hadoop;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.GroupMappingServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class StackableGroupMapper implements GroupMappingServiceProvider {
    private static final String OPA_MAPPING_URL_PROP = "hadoop.security.group.mapping.opa.url";
    private final Logger LOG = LoggerFactory.getLogger(StackableGroupMapper.class);
    private final Configuration configuration;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper json;

    public StackableGroupMapper() {
        this.configuration = new Configuration();
        this.json = new ObjectMapper()
                // https://github.com/stackabletech/trino-opa-authorizer/issues/24
                // OPA server can send other fields, such as `decision_id`` when enabling decision logs
                // We could add all the fields we *currently* know, but it's more future-proof to ignore any unknown fields.
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

        String opaMappingUrl = configuration.get(OPA_MAPPING_URL_PROP);

        if (opaMappingUrl == null) {
            throw new RuntimeException("Config \"" + OPA_MAPPING_URL_PROP + "\" missing");
        }

        URI opaUri = URI.create(opaMappingUrl);
        HttpResponse<String> response = null;

        OpaQuery query = new OpaQuery(new OpaQuery.OpaQueryInput(user));
        String body = json.writeValueAsString(query);

        LOG.debug("Request body [{}]", body);
        try {
            response = httpClient.send(
                    HttpRequest.newBuilder(opaUri).header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            LOG.info("Opa response [{}]", response.body());
        } catch (InterruptedException e) {
            LOG.error(e.getMessage());
        }

        if (response == null || response.statusCode() != 200) {
            throw new IOException(opaUri.toString());
        }
        String responseBody = response.body();
        LOG.debug("Response body [{}]", responseBody);

        OpaQueryResult result = json.readValue(responseBody, OpaQueryResult.class);
        LOG.info("Groups for [{}]: [{}]", user, result.groups);

        return result.groups;
    }

    /**
     * Caches groups, no need to do that for this provider
     */
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
        LOG.info("ignoring cacheGroupsAdd: caching should be provided by the policy provider");
    }
}
