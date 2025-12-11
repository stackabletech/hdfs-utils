package tech.stackable.hadoop;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Manages all caching layers for the topology provider. */
public class TopologyCache {
  private final Cache<String, String> topology;
  private final Cache<String, Node> nodes;
  private final Cache<String, GenericKubernetesResource> listeners;
  private final Cache<String, Pod> pods;

  TopologyCache(int expirationSeconds, int defaultExpirationSeconds) {
    this.topology =
        Caffeine.newBuilder().expireAfterWrite(expirationSeconds, TimeUnit.SECONDS).build();

    this.nodes =
        Caffeine.newBuilder().expireAfterWrite(defaultExpirationSeconds, TimeUnit.SECONDS).build();

    this.listeners =
        Caffeine.newBuilder().expireAfterWrite(defaultExpirationSeconds, TimeUnit.SECONDS).build();

    this.pods =
        Caffeine.newBuilder().expireAfterWrite(defaultExpirationSeconds, TimeUnit.SECONDS).build();
  }

  String getTopology(String key) {
    return topology.getIfPresent(key);
  }

  void putTopology(String key, String value) {
    topology.put(key, value);
  }

  void invalidateAllTopologyKeys() {
    topology.invalidateAll();
  }

  void invalidateTopologyKeys(List<String> keys) {
    keys.forEach(topology::invalidate);
  }

  Node getNode(String name) {
    return nodes.getIfPresent(name);
  }

  void putNode(String name, Node node) {
    nodes.put(name, node);
  }

  GenericKubernetesResource getListener(String name) {
    return listeners.getIfPresent(name);
  }

  void putListener(String name, GenericKubernetesResource listener) {
    listeners.put(name, listener);
  }

  Pod getPod(String name) {
    return pods.getIfPresent(name);
  }

  void putPod(String name, Pod pod) {
    pods.put(name, pod);
  }

  boolean hasAllPods(List<String> names) {
    return names.stream().noneMatch(name -> pods.getIfPresent(name) == null);
  }
}
