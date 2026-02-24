package tech.stackable.hadoop;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyUtils {
  private static final Logger LOG = LoggerFactory.getLogger(TopologyUtils.class);

  private static final String ADDRESS = "address";
  private static final String STATUS = "status";
  private static final String INGRESS_ADDRESSES = "ingressAddresses";

  public static List<String> getIngressAddresses(GenericKubernetesResource listener) {
    // suppress warning as we know the structure of our own listener resource
    Object statusObj = listener.getAdditionalProperties().get(STATUS);
    if (statusObj == null) {
      LOG.warn("Listener {} has no status", listener.getMetadata().getName());
      return Collections.emptyList();
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> status = (Map<String, Object>) statusObj;
    Object addressesObj = status.get(INGRESS_ADDRESSES);
    if (addressesObj == null) {
      LOG.warn("Listener {} has no ingress addresses", listener.getMetadata().getName());
      return Collections.emptyList();
    }
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> ingressAddresses = (List<Map<String, Object>>) addressesObj;
    return ingressAddresses.stream()
        .map(ingress -> (String) ingress.get(ADDRESS))
        .collect(Collectors.toList());
  }

  public static int parseIntFromEnv(String varName, int defaultValue, String description) {
    String value = System.getenv(varName);
    if (value == null || value.isEmpty()) {
      LOG.info("Set {} to default value {}", description, defaultValue);
      return defaultValue;
    }

    try {
      int parsed = Integer.parseInt(value);
      LOG.info("Set {} to {} from environment variable {}", description, parsed, varName);
      return parsed;
    } catch (NumberFormatException e) {
      LOG.warn(
          "Invalid integer value '{}' for {} - using default: {}", value, varName, defaultValue);
      return defaultValue;
    }
  }
}
