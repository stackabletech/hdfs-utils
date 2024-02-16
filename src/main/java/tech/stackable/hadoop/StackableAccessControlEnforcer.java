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
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

// As of 2024-02-09 INodeAttributeProvider.AccessControlEnforcer has two functions: The old - deprecated -
// checkPermission and the new checkPermissionWithContext. HDFS uses reflection to check if the authorizer
// supports the new API (which we do) and uses that in this case. This is also indicated by the log statement
// "Use the new authorization provider API" during startup, see https://github.com/apache/hadoop/blob/50d256ef3c2531563bc6ba96dec6b78e154b4697/hadoop-hdfs-project/hadoop-hdfs/src/main/java/org/apache/hadoop/hdfs/server/namenode/FSDirectory.java#L245
// FSPermissionChecker (as a caller of the AccessControlEnforcer interface) has a ThreadLocal operationType, which
// needs to be set to e.g. "create", "delete" or "rename" prior to calling the FSPermissionChecker.checkPermission
// function, as it will actually check if operationType is null and will still use the old API in this case! But the old
// API does not have the information about the operationType, which makes it hard to impossible to authorize the request.
// As a consequence we only support the new API and will make sure no HDFS code path calls the old API. This required
// minor patches to HDFS, as it was e.g. missing a call to FSPermissionChecker.setOperationType("create") in
// FSNamesystem.startFileInt (this claim needs to be validated though).
public class StackableAccessControlEnforcer implements INodeAttributeProvider.AccessControlEnforcer {

    private static final Logger LOG = LoggerFactory.getLogger(StackableAccessControlEnforcer.class);

    public static final String OPA_POLICY_URL_PROP = "hadoop.security.authorization.opa.policy.url";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper json;
    private URI opaUri;

    public StackableAccessControlEnforcer() {
        LOG.debug("Starting StackableAccessControlEnforcer");

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

        LOG.debug("Started HdfsOpaAccessControlEnforcer");
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
        LOG.warn("checkPermission called");

        new Throwable().printStackTrace();
        throw new AccessControlException("The HdfsOpaAccessControlEnforcer does not implement the old checkPermission API. " +
                "This should not happen, as all HDFS code paths should call the new API. " +
                "I dumped the stack trace for you (check active namenode logs), so you can figure out which code path it was. " +
                "Please report all of that to author of the OPA authorizer (We don't have a stable GitHub link yet, sorry!) " +
                "Passed arguments: " +
                "fsOwner: " + fsOwner + ", supergroup: " + supergroup + ", ugi: " + ugi + ", path: " + path + ", ancestorIndex:" + ancestorIndex +
                ", doCheckOwner: " + doCheckOwner + ", ancestorAccess: " + ancestorAccess + ", parentAccess: " + parentAccess +
                ", subAccess: " + subAccess + ", ignoreEmptyDir: " + ignoreEmptyDir);
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

        String prettyPrinted;
        try {
            prettyPrinted = json.writerWithDefaultPrettyPrinter().writeValueAsString(query);
        } catch (JsonProcessingException e) {
            throw new OpaException.SerializeFailed(e);
        }

        LOG.debug("Request body:\n{}", prettyPrinted);
        HttpResponse<String> response = null;
        try {
            response =
                    httpClient.send(
                            HttpRequest.newBuilder(opaUri)
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(body))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            LOG.debug("Opa response: {}", response.body());
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
