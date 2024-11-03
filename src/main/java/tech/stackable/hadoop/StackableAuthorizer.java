package tech.stackable.hadoop;

import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StackableAuthorizer extends INodeAttributeProvider {

  private static final Logger LOG = LoggerFactory.getLogger(StackableAuthorizer.class);

  private static final StackableAccessControlEnforcer ENFORCER =
      new StackableAccessControlEnforcer();

  @Override
  public void start() {
    LOG.debug("Starting HdfsOpaAuthorizer");
  }

  @Override
  public void stop() {
    LOG.debug("Stopping HdfsOpaAuthorizer");
  }

  @Override
  public INodeAttributes getAttributes(String[] strings, INodeAttributes iNodeAttributes) {
    // No special attributes needed
    return iNodeAttributes;
  }

  @Override
  public AccessControlEnforcer getExternalAccessControlEnforcer(
      AccessControlEnforcer defaultEnforcer) {
    return ENFORCER;
  }
}
