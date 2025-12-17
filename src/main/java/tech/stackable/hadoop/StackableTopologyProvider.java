package tech.stackable.hadoop;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.apache.hadoop.net.DNSToSwitchMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the org.apache.hadoop.net.DNSToSwitchMapping that is used to create a
 * topology out of dataNodes.
 *
 * <p>This class is intended to be run as part of the NameNode process (in the same namespace) and
 * will be used by the nameNode to retrieve topology strings for dataNodes.
 */
public class StackableTopologyProvider implements DNSToSwitchMapping {
  private final Logger LOG = LoggerFactory.getLogger(StackableTopologyProvider.class);

  // Environment variable names
  public static final String VARNAME_CACHE_EXPIRATION = "TOPOLOGY_CACHE_EXPIRATION_SECONDS";

  // Default values
  public static final String DEFAULT_RACK = "/defaultRack";
  private static final int CACHE_EXPIRY_DEFAULT_SECONDS = 5 * 60;
  private static final int INFORMER_POLL_SECONDS = 30;
  // Cache on first usage (not on start-up to avoid attempts before listeners are available)
  private String listenerVersion;

  private final KubernetesClient client;
  private final List<TopologyLabel> labels;
  private final SharedInformerFactory sharedInformerFactory;

  // Caching layers
  private final TopologyCache cache;

  public StackableTopologyProvider() {
    // By default, the client will operate within the current namespace
    this.client = new KubernetesClientBuilder().build();
    this.cache = new TopologyCache(getCacheExpiration(), CACHE_EXPIRY_DEFAULT_SECONDS);
    this.labels = TopologyLabel.initializeTopologyLabels();
    this.sharedInformerFactory = client.informers();
    startPodInformer();

    logInitializationStatus();
  }

  @Override
  public void reloadCachedMappings() {
    // TODO: According to the upstream comment we should rebuild all cache entries after
    // invalidating them. This may mean trying to resolve ip addresses that do not exist
    // any more and things like that though and require some more thought, so we will for
    // now just invalidate the cache.
    this.cache.invalidateAllTopologyKeys();
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
    LOG.debug("Client namespace {}", client.getNamespace());
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
    // Unless we can answer everything from the cache we will perform a full resolution.
    List<String> cached = names.stream().map(cache::getTopology).collect(Collectors.toList());
    LOG.debug("Cached topologyKeyCache values [{}]", cached);

    return cached.contains(null) ? null : cached;
  }

  // ============================================================================
  // RESOLUTION WORKFLOW
  // ============================================================================

  private List<String> performFullResolution(List<String> names) {
    LOG.debug("Performing full topology resolution for: {}", names);

    // Pre-requisites : fetch all dataNodes and build label lookup maps from them
    List<Pod> dataNodes = fetchDataNodes();
    Map<String, Map<String, String>> podLabels = buildPodLabelMap(dataNodes);
    Map<String, Map<String, String>> nodeLabels = buildNodeLabelMap(dataNodes);

    // Build node-to-datanode map for O(1) colocated lookups
    Map<String, String> nodeToDatanodeIp = buildNodeToDatanodeMap(dataNodes);

    List<String> topologies = new ArrayList<>();
    // Resolve masqueraded IPs to nodes: do this before inspecting possible listener-
    // or other pod-IPs as we don't want to mistakenly treat a masqueraded IP as a
    // cache-miss. Examine each name in a loop, so we have the chance to short-circuit.
    for (String name : names) {
      String datanodeIp = tryNodeOrListenerOrPod(name, nodeToDatanodeIp, podLabels);
      // Build topology strings and cache results
      String topology = buildAndCacheTopology(name, datanodeIp, podLabels, nodeLabels);
      topologies.add(topology);
    }

    return topologies;
  }

  private String tryNodeOrListenerOrPod(
      String name,
      Map<String, String> nodeToDatanodeIp,
      Map<String, Map<String, String>> podLabels) {
    String dataNodeIp = nodeToDatanodeIp.get(name);
    if (dataNodeIp != null) {
      return dataNodeIp;
    } else {
      // If a simple dataNode lookup does not work, we have to decide whether we
      // want to have the overhead of fetching the listeners, or of fetching all
      // pods in the namespace. Opt for listeners first, as there are typically
      // fewer of them.
      String resolvedListener = tryResolveListener(name);
      if (resolvedListener != null) {
        return resolvedListener;
      } else {
        return tryResolveClientPodToDataNode(name, podLabels, nodeToDatanodeIp);
      }
    }
  }

  private String buildAndCacheTopology(
      String originalName,
      String datanodeIp,
      Map<String, Map<String, String>> podLabels,
      Map<String, Map<String, String>> nodeLabels) {

    String topology = buildTopologyString(datanodeIp, podLabels, nodeLabels);

    // Cache both the resolved IP and original name
    cache.putTopology(datanodeIp, topology);
    cache.putTopology(originalName, topology);

    LOG.info("Built topology: {}", topology);
    return topology;
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

  private String getListenerVersion() {
    try {
      var crd =
          client
              .apiextensions()
              .v1()
              .customResourceDefinitions()
              .withName("listeners.listeners.stackable.tech")
              .get();

      if (crd != null && !crd.getSpec().getVersions().isEmpty()) {
        // Select the version that is served and used for storage (the "stable" version)
        for (var version : crd.getSpec().getVersions()) {
          if (version.getServed() && version.getStorage()) {
            LOG.debug("Returning served/stored version: {}", version.getName());
            return version.getName();
          }
        }
        // If no stable version found, return the first served version as a fallback
        for (var version : crd.getSpec().getVersions()) {
          if (version.getServed()) {
            LOG.debug("Returning served/un-stored version: {}", version.getName());
            return version.getName();
          }
        }
      }
      LOG.error("Unable to fetch CRD version for listeners. Returning default value.");
      return "v1alpha1";
    } catch (KubernetesClientException e) {
      LOG.error("Unable to fetch CRD version for listeners. Throwing exception.", e);
      throw new RuntimeException("Unable to fetch CRD version for listeners");
    }
  }

  private String tryResolveListener(String name) {
    refreshListenerCacheIfNeeded(name);

    return tryResolveListenerToDatanode(name);
  }

  private void refreshListenerCacheIfNeeded(String name) {
    if (cache.getListener(name) != null) {
      LOG.debug("Listener cache contains all required entries");
      return;
    }

    // Listeners are typically few, so fetch all
    LOG.debug("Fetching all listeners to populate cache");
    if (listenerVersion == null) {
      listenerVersion = getListenerVersion();
      LOG.debug("Fetching all listeners in version {}", listenerVersion);
    }
    GenericKubernetesResourceList listeners = fetchListeners(listenerVersion);

    for (GenericKubernetesResource listener : listeners.getItems()) {
      cacheListenerByNameAndAddresses(listener);
    }
  }

  private void cacheListenerByNameAndAddresses(GenericKubernetesResource listener) {
    String name = listener.getMetadata().getName();
    LOG.debug("Caching listener by name: {}", name);
    cache.putListener(name, listener);

    // Also cache by ingress addresses for quick lookup
    for (String address : TopologyUtils.getIngressAddresses(listener)) {
      LOG.debug("Caching listener by address: {}", address);
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
  private String tryResolveListenerToDatanode(String name) {
    GenericKubernetesResource listener = cache.getListener(name);
    if (listener == null) {
      LOG.debug("Not a listener: {}", name);
      return null;
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

  private GenericKubernetesResourceList fetchListeners(String listenerVersion) {
    ResourceDefinitionContext listenerCrd =
        new ResourceDefinitionContext.Builder()
            .withGroup("listeners.stackable.tech")
            .withVersion(listenerVersion)
            .withPlural("listeners")
            .withNamespaced(true)
            .build();

    return client.genericKubernetesResources(listenerCrd).list();
  }

  // ============================================================================
  // CLIENT POD RESOLUTION
  // ============================================================================

  private String tryResolveClientPodToDataNode(
      String name,
      Map<String, Map<String, String>> podLabels,
      Map<String, String> nodeToDatanodeIp) {

    refreshPodCacheIfNeeded(name);

    return resolveToDatanodeOrKeep(name, podLabels, nodeToDatanodeIp);
  }

  private void refreshPodCacheIfNeeded(String name) {
    if (cache.getPod(name) != null) {
      LOG.debug("Pod cache contains entry");
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
   * co-located dataNodes for client pods. This map will contain as keys both the node name and all
   * its addresses (as the address may be used by pods with IP masquerading).
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
        LOG.debug("Assigned to node-name [{}/{}]", nodeName, dataNodeIp);
        nodeToDatanode.put(nodeName, dataNodeIp);
        Node node = getOrFetchNode(nodeName);
        for (NodeAddress nodeAddress : node.getStatus().getAddresses()) {
          LOG.debug("Assigned to node-address [{}/{}]", nodeAddress.getAddress(), dataNodeIp);
          nodeToDatanode.put(nodeAddress.getAddress(), dataNodeIp);
        }
      }
    }

    LOG.debug("Built node-to-datanode map {}", nodeToDatanode);
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
   * to. It will also do this for the node addresses as calling pods may masquerade as node IPs.
   *
   * @param dataNodes List of all in-scope dataNodes (datanode pods in this namespace)
   * @return Map of ip addresses to labels of the node running the pod that the ip address belongs
   *     to
   */
  private Map<String, Map<String, String>> buildNodeLabelMap(List<Pod> dataNodes) {
    Map<String, Map<String, String>> result = new HashMap<>();
    for (Pod dataNode : dataNodes) {
      String nodeName = dataNode.getSpec().getNodeName();

      if (nodeName == null) {
        LOG.warn("Pod [{}] not yet assigned to node, retrying", dataNode.getMetadata().getName());
        return result;
      }

      Node node = getOrFetchNode(nodeName);
      Map<String, String> nodeLabels = node.getMetadata().getLabels();
      LOG.debug("Labels for node [{}]:[{}]....", nodeName, nodeLabels);

      for (NodeAddress nodeAddress : node.getStatus().getAddresses()) {
        LOG.debug("...assigned to node address [{}]", nodeAddress.getAddress());
        result.put(nodeAddress.getAddress(), nodeLabels);
      }

      for (PodIP podIp : dataNode.getStatus().getPodIPs()) {
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
      LOG.debug("Labels for pod [{}]:[{}]....", pod.getMetadata().getName(), podLabels);

      for (PodIP podIp : pod.getStatus().getPodIPs()) {
        LOG.debug("...assigned to pod IP [{}]", podIp.getIp());
        result.put(podIp.getIp(), podLabels);
      }
    }
    return result;
  }

  // ============================================================================
  // INFORMERS
  // ============================================================================

  private void startPodInformer() {
    client
        .pods()
        .inNamespace(client.getNamespace())
        .inform(
            new ResourceEventHandler<>() {
              @Override
              public void onAdd(Pod pod) {
                cache.putPod(pod.getMetadata().getName(), pod);
                for (PodIP ip : pod.getStatus().getPodIPs()) {
                  cache.putPod(ip.getIp(), pod);
                }
                LOG.info("Pod {} added", pod.getMetadata().getName());
              }

              @Override
              public void onUpdate(Pod oldPod, Pod newPod) {
                cache.putPod(oldPod.getMetadata().getName(), newPod);
                for (PodIP ip : oldPod.getStatus().getPodIPs()) {
                  cache.putPod(ip.getIp(), newPod);
                }
                LOG.info("Pod {} updated", oldPod.getMetadata().getName());
              }

              @Override
              public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
                cache.deletePod(pod.getMetadata().getName());
                for (PodIP ip : pod.getStatus().getPodIPs()) {
                  cache.deletePod(ip.getIp());
                }
                LOG.info("Pod {} deleted", pod.getMetadata().getName());
              }
            },
            INFORMER_POLL_SECONDS * 1000L);

    Future<Void> future = sharedInformerFactory.startAllRegisteredInformers();

    try {
      // this will block until complete
      LOG.debug("Waiting for informer registration to complete...");
      future.get();
    } catch (InterruptedException e) {
      LOG.error("Pod Informer initialization was interrupted", e);
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      LOG.error("Pod Informer initialization encountered an exception", e);
      throw new RuntimeException(e);
    }
    LOG.info("Pod Informer initialized.");
  }
}
