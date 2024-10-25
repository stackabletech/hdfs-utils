package tech.stackable.hadoop;

import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider;
import org.apache.hadoop.security.UserGroupInformation;

public class OpaReducedAllowQuery {
  public final OpaReducedAllowQueryInput input;

  public OpaReducedAllowQuery(OpaReducedAllowQueryInput input) {
    this.input = input;
  }

  /**
   * Similar to {@link OpaAllowQuery.OpaAllowQueryInput}, but  this class only contains a subset of   * fields that
   * should be sufficient for most use-cases, but offer a much better performance.
   * See <a href="https://github.com/stackabletech/hdfs-utils/issues/48">this issue</a> for details.
   */
  public static class OpaReducedAllowQueryInput {
    public String fsOwner;
    public String supergroup;
    // Wrapping this
    public OpaQueryUgi callerUgi;
    public int snapshotId;
    public String path;
    public int ancestorIndex;
    public boolean doCheckOwner;
    public boolean ignoreEmptyDir;
    public String operationName;
    public org.apache.hadoop.ipc.CallerContext callerContext;

    public OpaReducedAllowQueryInput(INodeAttributeProvider.AuthorizationContext context) {
      this.fsOwner = context.getFsOwner();
      this.supergroup = context.getSupergroup();
      this.callerUgi = new OpaQueryUgi(context.getCallerUgi());
      this.snapshotId = context.getSnapshotId();
      this.path = context.getPath();
      this.ancestorIndex = context.getAncestorIndex();
      this.doCheckOwner = context.isDoCheckOwner();
      this.ignoreEmptyDir = context.isIgnoreEmptyDir();
      this.operationName = context.getOperationName();
      this.callerContext = context.getCallerContext();
    }
  }
}
