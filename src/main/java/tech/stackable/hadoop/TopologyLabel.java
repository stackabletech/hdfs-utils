package tech.stackable.hadoop;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyLabel {
  private static final Logger LOG = LoggerFactory.getLogger(TopologyLabel.class);
  public static final String VARNAME_LABELS = "TOPOLOGY_LABELS";
  public static final String VARNAME_MAXLEVELS = "TOPOLOGY_MAX_LEVELS";
  private static final int MAX_LEVELS_DEFAULT = 2;

  public enum Type {
    NODE,
    POD,
    UNDEFINED
  }

  private final Type type;
  private final String name;

  TopologyLabel(String config) {
    if (config == null || config.isEmpty()) {
      this.type = Type.UNDEFINED;
      this.name = null;
      return;
    }

    String[] parts = config.toLowerCase(Locale.ROOT).split(":", 2);

    if (parts.length != 2) {
      LOG.warn("Invalid topology label format '{}' - expected '[node|pod]:<labelname>'", config);
      this.type = Type.UNDEFINED;
      this.name = null;
      return;
    }

    this.name = parts[1];

    switch (parts[0]) {
      case "node":
        this.type = Type.NODE;
        break;
      case "pod":
        this.type = Type.POD;
        break;
      default:
        LOG.warn("Unsupported label type '{}' - must be 'node' or 'pod'", parts[0]);
        this.type = Type.UNDEFINED;
    }
  }

  boolean isNodeLabel() {
    return type == Type.NODE;
  }

  boolean isUndefined() {
    return type == Type.UNDEFINED;
  }

  String getName() {
    return name;
  }

  Type getType() {
    return type;
  }

  public static List<TopologyLabel> initializeTopologyLabels() {
    // Read the labels to be used to build a topology from environment variables. Labels are
    // configured in the EnvVar "TOPOLOGY_LABELS". They should be specified in the form
    // "[node|pod]:<labelname>" and separated by ";". So a valid configuration that reads topology
    // information from the labels "kubernetes.io/zone" and "kubernetes.io/rack" on the k8s node
    // that is running a datanode pod would look like this:
    // "node:kubernetes.io/zone;node:kubernetes.io/rack" By default, there is an upper limit of 2 on
    // the number of labels that are processed, because this is what Hadoop traditionally allows -
    // this can be overridden via setting the EnvVar "MAX_TOPOLOGY_LEVELS".
    String topologyConfig = System.getenv(VARNAME_LABELS);

    if (topologyConfig == null || topologyConfig.isEmpty()) {
      LOG.error(
          "Missing env var [{}] this is required for rack awareness to work.", VARNAME_LABELS);
      throw new RuntimeException("TOPOLOGY_LABELS environment variable not set");
    }

    String[] labelConfigs = topologyConfig.split(";");

    if (labelConfigs.length > getMaxLabels()) {
      LOG.error(
          "Found [{}] topology labels configured, but maximum allowed number is [{}]: "
              + "please check your config or raise the number of allowed labels.",
          labelConfigs.length,
          getMaxLabels());
      throw new RuntimeException("Too many topology labels configured");
    }
    // Create TopologyLabels from config strings
    List<TopologyLabel> labels =
        Arrays.stream(labelConfigs).map(TopologyLabel::new).collect(Collectors.toList());

    if (labels.stream().anyMatch(TopologyLabel::isUndefined)) {
      LOG.error(
          "Invalid topology label configuration - labels must be in format '[pod|node]:<labelname>'");
      throw new RuntimeException("Invalid topology label configuration");
    }

    return labels;
  }

  private static int getMaxLabels() {
    return TopologyUtils.parseIntFromEnv(
        VARNAME_MAXLEVELS, MAX_LEVELS_DEFAULT, "maximum topology levels");
  }
}
