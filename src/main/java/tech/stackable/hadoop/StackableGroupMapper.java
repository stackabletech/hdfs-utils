package tech.stackable.hadoop;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.apache.hadoop.security.GroupMappingServiceProvider;
import org.apache.hadoop.util.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class StackableGroupMapper implements GroupMappingServiceProvider {
    private static final String USERS_MAPPING_SUFFIX = "USERS_MAPPING_SUFFIX";
    private static final String USERS_MAPPING_TARGET = "USERS_MAPPING_TARGET";
    private final Logger LOG = LoggerFactory.getLogger(StackableGroupMapper.class);
    private final KubernetesClient client;

    public StackableGroupMapper() {
        this.client = new DefaultKubernetesClient();
    }

    /**
     * Returns list of groups for a user.
     *
     * @param user get groups for this user
     * @return list of groups for a given user
     */
    @Override
    public List<String> getGroups(String user) throws IOException {
        LOG.info("Calling StackableGroupMapper.getGroups...");

        String mappingSuffix = System.getenv(USERS_MAPPING_SUFFIX); // e.g. "v1/data/hdfs"
        String mappingTarget = System.getenv(USERS_MAPPING_TARGET); // e.g. "users" in {"result":{"users":"me,myself,I"}}

        LOG.info("Detected USERS_MAPPING_SUFFIX: [{}] and USERS_MAPPING_TARGET: [{}]", mappingSuffix, mappingTarget);

        List<ConfigMap> discovery = client
                .configMaps()
                .withLabel("app.kubernetes.io/component", "server")
                .withLabel("app.kubernetes.io/name", "opa")
                .withLabel("app.kubernetes.io/role-group", "discovery")
                .list()
                .getItems();
        LOG.info("Retrieved discovery config-map [{}]",
                discovery.stream().map(cm -> cm.getMetadata().getName()).collect(Collectors.toList()));

        List<Node> nodes = client.nodes().list().getItems();
        LOG.info("Retrieved nodes: " + nodes.stream().map(node -> node.getMetadata().getName()).collect(Collectors.toList()));

        String url = discovery.get(0).getData().get("OPA");
        LOG.info("Service: [{}]", url);


        List<ConfigMap> regoBundle = client
                .configMaps()
                .withLabel("opa.stackable.tech/bundle", "hdfs-group-mapping")
                .list()
                .getItems();
        LOG.info("Retrieved rego config-map [{}]",
                regoBundle.stream().map(cm -> cm.getMetadata().getName()).collect(Collectors.toList()));
        String apiVersion = regoBundle.get(0).getApiVersion();

        Service opa = client
                .services()
                .withLabel("app.kubernetes.io/name", "opa")
                .withLabel("app.kubernetes.io/component", "server")
                .withLabel("app.kubernetes.io/role-group", "default")
                .list()
                .getItems().get(0);

        //String serviceURL = client.services().inNamespace("default").withName(opa.getMetadata().getName())
        //        .getURL(apiVersion + "/data/hdfs");

        MixedOperation<Endpoints, EndpointsList, DoneableEndpoints, Resource<Endpoints, DoneableEndpoints>> endpoints =
                client.endpoints();
        LOG.info("EPs [{}]", endpoints);
        return Lists.newArrayList("me", "myself", "I");
    }

    /**
     * Caches groups, no need to do that for this provider
     */
    @Override
    public void cacheGroupsRefresh() {
        // does nothing in this provider of user to groups mapping
    }

    /**
     * Adds groups to cache, no need to do that for this provider
     *
     * @param groups unused
     */
    @Override
    public void cacheGroupsAdd(List<String> groups) {
        // does nothing in this provider of user to groups mapping
    }
}
