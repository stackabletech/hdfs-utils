package tech.stackable.hadoop;

import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributes;
import org.apache.hadoop.ipc.CallerContext;
import org.apache.hadoop.security.UserGroupInformation;

public class OpaAllowQuery {
  public final OpaAllowQueryInput input;

  public OpaAllowQuery(OpaAllowQueryInput input) {
    this.input = input;
  }

  /**
   * Wrapper around {@link INodeAttributeProvider.AuthorizationContext}, which uses our custom
   * wrapper around {@link UserGroupInformation}, {@link OpaQueryUgi}.
   */
  public static class OpaAllowQueryInput {
    public String fsOwner;
    public String supergroup;
    // Wrapping this
    public OpaQueryUgi callerUgi;
    public INodeAttributes[] inodeAttrs;
    public INode[] inodes;
    public byte[][] pathByNameArr;
    public int snapshotId;
    public String path;
    public int ancestorIndex;
    public boolean doCheckOwner;
    public FsAction ancestorAccess;
    public FsAction parentAccess;
    public FsAction access;
    public FsAction subAccess;
    public boolean ignoreEmptyDir;
    public String operationName;
    public CallerContext callerContext;

    public OpaAllowQueryInput(INodeAttributeProvider.AuthorizationContext context) {
      fsOwner = context.getFsOwner();
      supergroup = context.getSupergroup();
      callerUgi = new OpaQueryUgi(context.getCallerUgi());
      inodeAttrs = context.getInodeAttrs();
      inodes = context.getInodes();
      pathByNameArr = context.getPathByNameArr();
      snapshotId = context.getSnapshotId();
      path = context.getPath();
      ancestorIndex = context.getAncestorIndex();
      doCheckOwner = context.isDoCheckOwner();
      ancestorAccess = context.getAncestorAccess();
      parentAccess = context.getParentAccess();
      access = context.getAccess();
      subAccess = context.getSubAccess();
      ignoreEmptyDir = context.isIgnoreEmptyDir();
      operationName = context.getOperationName();
      callerContext = context.getCallerContext();
    }
  }
}
