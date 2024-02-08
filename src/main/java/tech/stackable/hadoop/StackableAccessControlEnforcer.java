package tech.stackable.hadoop;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.hdfs.server.blockmanagement.DatanodeDescriptor;
import org.apache.hadoop.hdfs.server.blockmanagement.DatanodeStorageInfo;
import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributes;
import org.apache.hadoop.ipc.CallerContext;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

public class StackableAccessControlEnforcer implements INodeAttributeProvider.AccessControlEnforcer {

    private static final Logger LOG = LoggerFactory.getLogger(StackableAccessControlEnforcer.class);

    public static final String OPA_POLICY_URL_PROP = "hadoop.security.authorization.opa.policy.url";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper json;
    private URI opaUri;

    public StackableAccessControlEnforcer() {
        LOG.info("Starting StackableAccessControlEnforcer");

        // Guaranteed to be only called once (Effective Java: Item 3)
        Configuration configuration = HadoopConfigSingleton.INSTANCE.getConfiguration();

        String opaPolicyUrl = configuration.get(OPA_POLICY_URL_PROP);
        if (opaPolicyUrl == null) {
            throw new OpaException.UriMissing(OPA_POLICY_URL_PROP);
        }

        try {
            this.opaUri = URI.create(opaPolicyUrl);
        } catch (Exception e) {
            throw new OpaException.UriInvalid(opaUri, e);
        }

        this.json = new ObjectMapper()
                // OPA server can send other fields, such as `decision_id`` when enabling decision logs
                // We could add all the fields we *currently* know, but it's more future-proof to ignore any unknown fields
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // Previously we were getting
                // Caused by: com.fasterxml.jackson.databind.exc.InvalidDefinitionException: No serializer found for class org.apache.hadoop.hdfs.util.EnumCounters and no properties discovered to create BeanSerializer (to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) (through reference chain: tech.stackable.HdfsOpaAccessControlEnforcer$ContextWrapper["inodeAttrs"]->org.apache.hadoop.hdfs.server.namenode.INodeDirectory[0]->org.apache.hadoop.hdfs.server.namenode.INodeDirectory["features"]->org.apache.hadoop.hdfs.server.namenode.DirectoryWithQuotaFeature[0]->org.apache.hadoop.hdfs.server.namenode.DirectoryWithQuotaFeature["spaceConsumed"]->org.apache.hadoop.hdfs.server.namenode.QuotaCounts["typeSpaces"])
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                // Only include the needed fields. HDFS has many classes with even more circular reference to remove
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.PUBLIC_ONLY)
                // We need to remove some circular pointers (e.g. root -> children[0] -> parent -> root)
                // Otherwise we get com.fasterxml.jackson.databind.JsonMappingException: Infinite recursion (StackOverflowError)
                .addMixIn(DatanodeDescriptor.class, DatanodeDescriptorMixin.class);

        LOG.info("Started HdfsOpaAccessControlEnforcer");
    }

    private static class OpaQueryResult {
        // Boxed Boolean to detect not-present vs explicitly false
        public Boolean result;
    }

    @Override
    public void checkPermission(String fsOwner, String supergroup,
                                UserGroupInformation ugi, INodeAttributes[] inodeAttrs,
                                INode[] inodes, byte[][] pathByNameArr, int snapshotId, String path,
                                int ancestorIndex, boolean doCheckOwner, FsAction ancestorAccess,
                                FsAction parentAccess, FsAction access, FsAction subAccess,
                                boolean ignoreEmptyDir) throws AccessControlException {
        LOG.info("checkPermission called");

        // We are using the new "checkPermissionWithContext" API, as indicated by the log statement
        // "Use the new authorization provider API". All the calls to this old function only happen when opType == null,
        // in which case we have no idea on what to authorize at, so we put in the operationName "deprecatedCheckPermissionApi".
        // Rego rules need to check for the maximum access level, as this can be potentially any operation.

        INodeAttributeProvider.AuthorizationContext.Builder builder =
                new INodeAttributeProvider.AuthorizationContext.Builder();
        builder.fsOwner(fsOwner).
                supergroup(supergroup).
                callerUgi(ugi).
                inodeAttrs(inodeAttrs).
                inodes(inodes).
                pathByNameArr(pathByNameArr).
                snapshotId(snapshotId).
                path(path).
                ancestorIndex(ancestorIndex).
                doCheckOwner(doCheckOwner).
                ancestorAccess(ancestorAccess).
                parentAccess(parentAccess).
                access(access).
                subAccess(subAccess).
                ignoreEmptyDir(ignoreEmptyDir).
                operationName("deprecatedCheckPermissionApi").
                callerContext(CallerContext.getCurrent());
        this.checkPermissionWithContext(builder.build());

//        throw new AccessControlException("The HdfsOpaAccessControlEnforcer does not implement the old checkPermission API. Passed arguments: "
//            + "fsOwner: " + fsOwner + ", supergroup: " + supergroup + ", ugi: " + ugi + ", path: " + path + ", ancestorIndex:" + ancestorIndex
//                + ", doCheckOwner: " + doCheckOwner + ", ancestorAccess: " + ancestorAccess + ", parentAccess: " + parentAccess
//                + ", subAccess: " + subAccess + ", ignoreEmptyDir: " + ignoreEmptyDir);
    }

    @Override
    public void checkPermissionWithContext(INodeAttributeProvider.AuthorizationContext authzContext) throws AccessControlException {
        OpaAllowQuery query = new OpaAllowQuery(new OpaAllowQuery.OpaAllowQueryInput(authzContext));

        String body;
        try {
            body = json.writeValueAsString(query);
        } catch (JsonProcessingException e) {
            throw new OpaException.SerializeFailed(e);
        }

        LOG.info("Request body [{}]", body);
        HttpResponse<String> response = null;
        try {
            response =
                    httpClient.send(
                            HttpRequest.newBuilder(opaUri)
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(body))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            LOG.debug("Opa response [{}]", response.body());
        } catch (Exception e) {
            LOG.error(e.getMessage());
            throw new OpaException.QueryFailed(e);
        }

        switch (Objects.requireNonNull(response).statusCode()) {
            case 200:
                break;
            case 404:
                throw new OpaException.EndPointNotFound(opaUri.toString());
            default:
                throw new OpaException.OpaServerError(query.toString(), response);
        }

        OpaQueryResult result;
        try {
            result = json.readValue(response.body(), OpaQueryResult.class);
        } catch (JsonProcessingException e) {
            throw new OpaException.DeserializeFailed(e);
        }

        if (result.result == null || !result.result) {
            throw new AccessControlException("OPA denied the request");
        }
    }

    private abstract static class DatanodeDescriptorMixin {
        @JsonIgnore
        abstract INode getParent();
        @JsonIgnore
        abstract DatanodeStorageInfo[] getStorageInfos();
    }
}
