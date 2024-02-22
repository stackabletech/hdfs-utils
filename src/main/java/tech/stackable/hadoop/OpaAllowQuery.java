package tech.stackable.hadoop;

import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider;
import org.apache.hadoop.security.UserGroupInformation;

public class OpaAllowQuery {
  public final OpaAllowQueryInput input;

  public OpaAllowQuery(OpaAllowQueryInput input) {
    this.input = input;
  }

  /**
   * Wrapper around {@link INodeAttributeProvider.AuthorizationContext}, which uses our custom wrapper around
   * {@link UserGroupInformation}, {@link OpaQueryUgi}.
   */
  public static class OpaAllowQueryInput {
    public java.lang.String fsOwner;
    public java.lang.String supergroup;
    // Wrapping this
    public OpaQueryUgi callerUgi;
    public org.apache.hadoop.hdfs.server.namenode.INodeAttributes[] inodeAttrs;
    public org.apache.hadoop.hdfs.server.namenode.INode[] inodes;
    public byte[][] pathByNameArr;
    public int snapshotId;
    public java.lang.String path;
    public int ancestorIndex;
    public boolean doCheckOwner;
    public org.apache.hadoop.fs.permission.FsAction ancestorAccess;
    public org.apache.hadoop.fs.permission.FsAction parentAccess;
    public org.apache.hadoop.fs.permission.FsAction access;
    public org.apache.hadoop.fs.permission.FsAction subAccess;
    public boolean ignoreEmptyDir;
    public java.lang.String operationName;
    public org.apache.hadoop.ipc.CallerContext callerContext;

    public OpaAllowQueryInput(INodeAttributeProvider.AuthorizationContext context) {
      this.fsOwner = context.getFsOwner();
      this.supergroup = context.getSupergroup();
      this.callerUgi = new OpaQueryUgi(context.getCallerUgi());
      this.inodeAttrs = context.getInodeAttrs();
      this.inodes = context.getInodes();
      this.pathByNameArr = context.getPathByNameArr();
      this.snapshotId = context.getSnapshotId();
      this.path = context.getPath();
      this.ancestorIndex = context.getAncestorIndex();
      this.doCheckOwner = context.isDoCheckOwner();
      this.ancestorAccess = context.getAncestorAccess();
      this.parentAccess = context.getParentAccess();
      this.access = context.getAccess();
      this.subAccess = context.getSubAccess();
      this.ignoreEmptyDir = context.isIgnoreEmptyDir();
      this.operationName = context.getOperationName();
      this.callerContext = context.getCallerContext();
    }
  }
}
