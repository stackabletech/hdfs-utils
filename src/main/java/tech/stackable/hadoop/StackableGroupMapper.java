package tech.stackable.hadoop;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.hadoop.security.GroupMappingServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class StackableGroupMapper implements GroupMappingServiceProvider {
    private KubernetesClient client;
    private final Logger LOG = LoggerFactory.getLogger(StackableGroupMapper.class);

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
        return null;
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
