package tech.stackable.hadoop;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.hadoop.net.DNSToSwitchMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the org.apache.hadoop.net.DNSToSwitchMapping that is used to create a
 * topology out of dataNodes.
 *
 * <p>This class is intended to be run as part of the NameNode process and will be used by the
 * nameNode to retrieve topology strings for dataNodes.
 */
public class StackableTopologyProvider implements DNSToSwitchMapping {
  private final Logger LOG = LoggerFactory.getLogger(StackableTopologyProvider.class);

  // Environment variable names
  public static final String VARNAME_CACHE_EXPIRATION = "TOPOLOGY_CACHE_EXPIRATION_SECONDS";

  // Default values
  public static final String DEFAULT_RACK = "/defaultRack";
  private static final int CACHE_EXPIRY_DEFAULT_SECONDS = 5 * 60;

  private final KubernetesClient client;
  private final List<TopologyLabel> labels;

  // Caching layers
  private final TopologyCache cache;

  public StackableTopologyProvider() {
    this.client = new KubernetesClientBuilder().build();
    this.cache = new TopologyCache(getCacheExpiration(), CACHE_EXPIRY_DEFAULT_SECONDS);
    this.labels = TopologyLabel.initializeTopologyLabels();

    logInitializationStatus();
  }

  @Override
  public void reloadCachedMappings() {
    // TODO: According to the upstream comment we should rebuild all cache entries after
    // invalidating them
    //  this may mean trying to resolve ip addresses that do not exist any more and things like that
    // though and
    //  require some more thought, so we will for now just invalidate the cache.
    this.cache.invalidateAll();
  }

  @Override
  public void reloadCachedMappings(List<String> names) {
    // TODO: See comment above, the same applies here
    cache.invalidateTopologyKeys(names);
  }

  private int getCacheExpiration() {
    return TopologyUtils.parseIntFromEnv(
        VARNAME_CACHE_EXPIRATION, CACHE_EXPIRY_DEFAULT_SECONDS, "cache expiration seconds");
  }

  private void logInitializationStatus() {
    if (labels.isEmpty()) {
      LOG.info("No topology configuration - will use default rack: {}", DEFAULT_RACK);
    } else {
      List<String> labelNames =
          labels.stream().map(TopologyLabel::getName).collect(Collectors.toList());
      LOG.info("Initialized with topology labels: {}", labelNames);
    }
    LOG.debug("Client namespaces {} and config {}", client.namespaces(), client.configMaps());
  }

  @Override
  public List<String> resolve(List<String> names) {
    LOG.info("Resolving topology for: {}", names);

    if (labels.isEmpty()) {
      return createDefaultRackList(names);
    }

    // Try to serve from cache first
    List<String> cachedValues = tryResolveFromCache(names);
    if (cachedValues != null) {
      LOG.info("Returning cached topology: {}", cachedValues);
      return cachedValues;
    }

    // Cache miss - perform full resolution
    return performFullResolution(names);
  }

  private List<String> createDefaultRackList(List<String> names) {
    LOG.info(
        "No topology labels defined, returning [{}] for hdfs nodes: [{}]", DEFAULT_RACK, names);
    return names.stream().map(name -> DEFAULT_RACK).collect(Collectors.toList());
  }

  private List<String> tryResolveFromCache(List<String> names) {
    // We need to check if we have cached values for all dataNodes contained in this request.
    // Unless we can answer everything from the cache we have to talk to k8s anyway and can just
    // recalculate everything
    List<String> cached = names.stream().map(cache::getTopology).collect(Collectors.toList());
    LOG.debug("Cached topologyKeyCache values [{}]", cached);

    return cached.contains(null) ? null : cached;
  }

  // ============================================================================
  // RESOLUTION WORKFLOW
  // ============================================================================

  private List<String> performFullResolution(List<String> names) {
    LOG.debug("Performing full topology resolution for: {}", names);

    // Step 1: Gather all dataNodes
    List<Pod> dataNodes = fetchDataNodes();

    // Step 2: Resolve listeners to actual datanode IPs
    List<String> resolvedNames = resolveListeners(names);

    // Step 3: Build label lookup maps
    Map<String, Map<String, String>> podLabels = buildPodLabelMap(dataNodes);
    Map<String, Map<String, String>> nodeLabels = buildNodeLabelMap(dataNodes);

    // Step 4: Build node-to-datanode map for O(1) colocated lookups
    Map<String, String> nodeToDatanodeIp = buildNodeToDatanodeMap(dataNodes);

    // Step 5: Resolve client pods to co-located dataNodes
    List<String> datanodeIps =
        resolveClientPodsToDataNodes(resolvedNames, podLabels, nodeToDatanodeIp);

    // Step 6: Build topology strings and cache results
    return buildAndCacheTopology(names, datanodeIps, podLabels, nodeLabels);
  }

  private List<String> buildAndCacheTopology(
      List<String> originalNames,
      List<String> datanodeIps,
      Map<String, Map<String, String>> podLabels,
      Map<String, Map<String, String>> nodeLabels) {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < datanodeIps.size(); i++) {
      String datanodeIp = datanodeIps.get(i);
      String originalName = originalNames.get(i);

      String topology = buildTopologyString(datanodeIp, podLabels, nodeLabels);
      result.add(topology);

      // Cache both the resolved IP and original name
      cache.putTopology(datanodeIp, topology);
      cache.putTopology(originalName, topology);
    }

    LOG.info("Built topology: {}", result);
    return result;
  }

  // ============================================================================
  // DATANODE FETCHING
  // ============================================================================

  private List<Pod> fetchDataNodes() {
    List<Pod> dataNodes =
        client
            .pods()
            .withLabel("app.kubernetes.io/component", "datanode")
            .withLabel("app.kubernetes.io/name", "hdfs")
            .list()
            .getItems();

    LOG.debug(
        "Retrieved dataNodes: [{}]",
        dataNodes.stream()
            .map(dataNode -> dataNode.getMetadata().getName())
            .collect(Collectors.toList()));
    return dataNodes;
  }

  // ============================================================================
  // LISTENER RESOLUTION
  // ============================================================================

  private List<String> resolveListeners(List<String> names) {
    refreshListenerCacheIfNeeded(names);

    return names.stream().map(this::resolveListenerToDatanode).collect(Collectors.toList());
  }

  private void refreshListenerCacheIfNeeded(List<String> names) {
    List<String> missingNames =
        names.stream().filter(name -> cache.getListener(name) == null).collect(Collectors.toList());

    if (missingNames.isEmpty()) {
      LOG.debug("Listener cache contains all required entries");
      return;
    }

    // Listeners are typically few, so fetch all
    // (Individual listener fetches would require knowing the namespace)
    LOG.debug("Fetching all listeners to populate cache");
    GenericKubernetesResourceList listeners = fetchListeners();

    for (GenericKubernetesResource listener : listeners.getItems()) {
      cacheListenerByNameAndAddresses(listener);
    }
  }

  private void cacheListenerByNameAndAddresses(GenericKubernetesResource listener) {
    String name = listener.getMetadata().getName();
    cache.putListener(name, listener);

    // Also cache by ingress addresses for quick lookup
    for (String address : TopologyUtils.getIngressAddresses(listener)) {
      cache.putListener(address, listener);
    }
  }

  /**
   * We don't know if the name refers to a listener (it could be any client pod) but we check to see
   * if it can be resolved to a dataNode just in case.
   *
   * @param name the name of the calling pod which should be resolved to a dataNode IP if it is a
   *     listener
   * @return either the name (for non-listener) or the dataNode IP to which this listener resolves
   */
  private String resolveListenerToDatanode(String name) {
    GenericKubernetesResource listener = cache.getListener(name);
    if (listener == null) {
      LOG.debug("Not a listener: {}", name);
      return name;
    }
    // We found a listener, so we can resolve it directly
    return resolveListenerEndpoint(listener);
  }

  private String resolveListenerEndpoint(GenericKubernetesResource listener) {
    String listenerName = listener.getMetadata().getName();
    Endpoints endpoint = client.endpoints().withName(listenerName).get();
    LOG.debug("Matched ingressAddress [{}]", listenerName);

    if (endpoint.getSubsets().isEmpty()) {
      LOG.warn("Endpoint {} has no subsets - pod may be restarting", listenerName);
      return listenerName;
    }

    EndpointAddress address = endpoint.getSubsets().get(0).getAddresses().get(0);
    LOG.info(
        "Resolved listener {} to IP {} on node {}",
        listenerName,
        address.getIp(),
        address.getNodeName());

    return address.getIp();
  }

  private GenericKubernetesResourceList fetchListeners() {
    ResourceDefinitionContext listenerCrd =
        new ResourceDefinitionContext.Builder()
            .withGroup("listeners.stackable.tech")
            .withVersion("v1alpha1")
            .withPlural("listeners")
            .withNamespaced(true)
            .build();

    return client.genericKubernetesResources(listenerCrd).list();
  }

  // ============================================================================
  // CLIENT POD RESOLUTION
  // ============================================================================

  private List<String> resolveClientPodsToDataNodes(
      List<String> names,
      Map<String, Map<String, String>> podLabels,
      Map<String, String> nodeToDatanodeIp) {

    refreshPodCacheIfNeeded(names);

    return names.stream()
        .map(name -> resolveToDatanodeOrKeep(name, podLabels, nodeToDatanodeIp))
        .collect(Collectors.toList());
  }

  private void refreshPodCacheIfNeeded(List<String> names) {
    if (cache.hasAllPods(names)) {
      LOG.debug("Pod cache contains all required entries");
      return;
    }

    // Note: We fetch all pods here because:
    // 1. Client pods (Spark, etc.) are queried by IP, not name
    // 2. K8s doesn't support "get pod by IP" - we must list and filter
    LOG.debug("Refreshing pod cache for client pod resolution");
    for (Pod pod : client.pods().list().getItems()) {
      cachePodByNameAndIps(pod);
    }
  }

  private void cachePodByNameAndIps(Pod pod) {
    String podName = pod.getMetadata().getName();
    LOG.debug("Refreshing pod cache: adding {}", podName);
    cache.putPod(podName, pod);

    // Cache by all IPs - this is crucial for IP-based lookups
    for (PodIP ip : pod.getStatus().getPodIPs()) {
      cache.putPod(ip.getIp(), pod);
    }
  }

  private String resolveToDatanodeOrKeep(
      String name,
      Map<String, Map<String, String>> podLabels,
      Map<String, String> nodeToDatanodeIp) {

    String ipAddress = resolveToIpAddress(name);

    // If it's already a datanode, return its IP
    if (podLabels.containsKey(ipAddress)) {
      LOG.info("Name is a datanode: {}", ipAddress);
      return ipAddress;
    }

    // Try to find co-located datanode
    Pod clientPod = cache.getPod(ipAddress);
    if (clientPod != null) {
      String datanodeIp = findColocatedDatanode(clientPod, nodeToDatanodeIp);
      if (datanodeIp != null) {
        return datanodeIp;
      }
    }

    // Keep original if we can't resolve
    return ipAddress;
  }

  private String findColocatedDatanode(Pod clientPod, Map<String, String> nodeToDatanodeIp) {
    String clientNodeName = clientPod.getSpec().getNodeName();

    if (clientNodeName == null) {
      LOG.warn("Client pod {} not yet assigned to node", clientPod.getMetadata().getName());
      return null;
    }

    String datanodeIp = nodeToDatanodeIp.get(clientNodeName);
    if (datanodeIp == null) {
      LOG.debug("No datanode found on node {}", clientNodeName);
    }

    return datanodeIp;
  }

  private String resolveToIpAddress(String hostname) {
    try {
      InetAddress address = InetAddress.getByName(hostname);
      String ip = address.getHostAddress();
      LOG.debug("Resolved {} to {}", hostname, ip);
      return ip;
    } catch (UnknownHostException e) {
      LOG.warn("Failed to resolve address: {} - defaulting to {}", hostname, DEFAULT_RACK);
      return hostname;
    }
  }

  /**
   * Build a map from Kubernetes node name to datanode IP. This enables O(1) lookup when finding
   * co-located dataNodes for client pods.
   *
   * <p>Note: If multiple dataNodes run on the same node, the last one wins. This is acceptable
   * because all dataNodes on the same node have the same topology.
   */
  private Map<String, String> buildNodeToDatanodeMap(List<Pod> dataNodes) {
    Map<String, String> nodeToDatanode = new HashMap<>();

    for (Pod dataNode : dataNodes) {
      String nodeName = dataNode.getSpec().getNodeName();
      String dataNodeIp = dataNode.getStatus().getPodIP();

      if (nodeName != null && dataNodeIp != null) {
        nodeToDatanode.put(nodeName, dataNodeIp);
      }
    }

    LOG.debug("Built node-to-datanode map with {} entries", nodeToDatanode.size());
    return nodeToDatanode;
  }

  // ============================================================================
  // TOPOLOGY STRING BUILDING
  // ============================================================================

  private String buildTopologyString(
      String ipAddress,
      Map<String, Map<String, String>> podLabels,
      Map<String, Map<String, String>> nodeLabels) {
    StringBuilder topology = new StringBuilder();

    for (TopologyLabel label : labels) {
      String labelValue = extractLabelValue(ipAddress, label, podLabels, nodeLabels);
      topology.append("/").append(labelValue);
    }

    String result = topology.toString();
    LOG.debug("Returning label [{}]", result);
    return result;
  }

  private String extractLabelValue(
      String ipAddress,
      TopologyLabel label,
      Map<String, Map<String, String>> podLabels,
      Map<String, Map<String, String>> nodeLabels) {

    Map<String, Map<String, String>> labelSource = label.isNodeLabel() ? nodeLabels : podLabels;

    String labelValue =
        labelSource
            .getOrDefault(ipAddress, Collections.emptyMap())
            .getOrDefault(label.getName(), "NotFound");

    LOG.debug("Label {}.{} = {}", label.getType(), label.getName(), labelValue);
    return labelValue;
  }

  // ============================================================================
  // LABEL MAPS
  // ============================================================================

  /**
   * Given a list of dataNodes this function will resolve which dataNodes run on which node as well
   * as all the ips assigned to a dataNodes. It will then return a mapping of every ip address to
   * the labels that are attached to the "physical" node running the dataNodes that this ip belongs
   * to.
   *
   * @param dataNodes List of all in-scope dataNodes (datanode pods in this namespace)
   * @return Map of ip addresses to labels of the node running the pod that the ip address belongs
   *     to
   */
  private Map<String, Map<String, String>> buildNodeLabelMap(List<Pod> dataNodes) {
    Map<String, Map<String, String>> result = new HashMap<>();
    for (Pod pod : dataNodes) {
      String nodeName = pod.getSpec().getNodeName();

      if (nodeName == null) {
        LOG.warn("Pod [{}] not yet assigned to node, retrying", pod.getMetadata().getName());
        return result;
      }

      Node node = getOrFetchNode(nodeName);
      Map<String, String> nodeLabels = node.getMetadata().getLabels();
      LOG.debug("Labels for node [{}]:[{}]....", nodeName, nodeLabels);

      for (PodIP podIp : pod.getStatus().getPodIPs()) {
        LOG.debug("...assigned to IP [{}]", podIp.getIp());
        result.put(podIp.getIp(), nodeLabels);
      }
    }
    return result;
  }

  private Node getOrFetchNode(String nodeName) {
    Node node = cache.getNode(nodeName);
    if (node == null) {
      LOG.debug("Fetching node: {}", nodeName);
      node = client.nodes().withName(nodeName).get();
      cache.putNode(nodeName, node);
    }
    return node;
  }

  /**
   * Given a list of dataNodes, return a HashMap that maps pod ips onto Pod labels. The returned Map
   * may contain more entries than the list that is given to this function, as an entry will be
   * generated for every ip a pod has.
   *
   * @param dataNodes List of all retrieved pods.
   * @return Map of ip addresses to all labels the pod that "owns" that ip has attached to itself
   */
  private Map<String, Map<String, String>> buildPodLabelMap(List<Pod> dataNodes) {
    Map<String, Map<String, String>> result = new HashMap<>();
    for (Pod pod : dataNodes) {
      Map<String, String> podLabels = pod.getMetadata().getLabels();

      for (PodIP podIp : pod.getStatus().getPodIPs()) {
        result.put(podIp.getIp(), podLabels);
      }
    }
    return result;
  }
}
