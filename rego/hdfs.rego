package hdfs

import rego.v1

default allow = false

# HDFS authorizer
allow if {
    some acl in acls
    matches_identity(input.callerUgi.shortUserName, acl.identity)
    matches_resource(input.path, acl.resource)
    action_sufficient_for_operation(acl.action, input.operationName)
}

# HDFS group mapper (this returns a list of strings)
groups := {group |
    raw = groups_for_user[input.username][_]
    # Keycloak groups have trailing slashes
    group := trim_prefix(raw, "/")
}

# Identity mentions the user explicitly
matches_identity(user, identity) if {
    identity == concat("", ["user:", user])
}

# Identity mentions group the user is part of
matches_identity(user, identity) if {
    some group in groups_for_user[user]
    identity == concat("", ["group:", group])
}

# Resource mentions the file explicitly
matches_resource(file, resource) if {
    resource == concat("", ["hdfs:file:", file])
}

# Resource mentions the directory explicitly
matches_resource(file, resource) if {
    trim_suffix(resource, "/") == concat("", ["hdfs:dir:", file])
}

# Resource mentions a folder higher up the tree, which will will grant access recursively
matches_resource(file, resource) if {
    startswith(resource, "hdfs:dir:/")
    # directories need to have a trailing slash
    endswith(resource, "/")
    startswith(file, trim_prefix(resource, "hdfs:dir:"))
}

action_sufficient_for_operation(action, operation) if {
    action_hierarchy[action][_] == action_for_operation[operation]
}

action_hierarchy := {
    "full": ["full", "rw", "ro"],
    "rw": ["rw", "ro"],
    "ro": ["ro"],
}

# To get a (hopefully complete) list of actions run "ack 'String operationName = '" in the hadoop source code
action_for_operation := {
    # The "rename" operation will be actually called on both - the source and the target location.
    # Because of this you need to have rw permissions on the source and target file - which is desired

    "abandonBlock": "rw",
    "addCacheDirective": "rw",
    "addCachePool": "full",
    "addErasureCodingPolicies": "full",
    "allowSnapshot": "full",
    "append": "rw",
    "cancelDelegationToken": "ro",
    "checkAccess": "ro",
    "clearQuota": "full",
    "clearSpaceQuota": "full",
    "completeFile": "rw",
    "computeSnapshotDiff": "full",
    "concat": "rw",
    "contentSummary": "ro",
    "create": "rw",
    "createEncryptionZone": "full",
    "createSnapshot": "full",
    "createSymlink": "rw",
    "delete": "rw",
    "deleteSnapshot": "full",
    "disableErasureCodingPolicy": "full",
    "disallowSnapshot": "full",
    "enableErasureCodingPolicy": "full",
    "finalizeRollingUpgrade": "full",
    "fsck": "full",
    "fsckGetBlockLocations": "full",
    "fsync": "rw",
    "gcDeletedSnapshot": "full",
    "getAclStatus": "ro",
    "getAdditionalBlock": "ro",
    "getAdditionalDatanode": "ro",
    "getDelegationToken": "ro",
    "getECTopologyResultForPolicies": "ro",
    "getErasureCodingCodecs": "ro",
    "getErasureCodingPolicies": "ro",
    "getErasureCodingPolicy": "ro",
    "getEZForPath": "ro",
    "getfileinfo": "ro",
    "getPreferredBlockSize": "ro",
    "getStoragePolicy": "ro",
    "getXAttrs": "ro",
    "isFileClosed": "ro",
    "listCacheDirectives": "ro",
    "listCachePools": "ro",
    "listCorruptFileBlocks": "ro",
    "listEncryptionZones": "ro",
    "listOpenFiles": "ro",
    "listReencryptionStatus": "ro",
    "ListSnapshot": "ro", # Yeah, this really starts with a capital letter
    "listSnapshottableDirectory": "ro",
    "listStatus": "ro",
    "listXAttrs": "ro",
    "mkdirs": "rw",
    "modifyAclEntries": "full",
    "modifyCacheDirective": "rw",
    "modifyCachePool": "full",
    "open": "ro",
    "queryRollingUpgrade": "ro",
    "quotaUsage": "ro",
    "recoverLease": "full",
    "reencryptEncryptionZone": "full",
    "removeAcl": "full",
    "removeAclEntries": "full",
    "removeCacheDirective": "rw",
    "removeCachePool": "full",
    "removeDefaultAcl": "full",
    "removeErasureCodingPolicy": "full",
    "removeXAttr": "rw",
    "rename": "rw",
    "renameSnapshot": "full",
    "renewDelegationToken": "ro",
    "satisfyStoragePolicy": "full",
    "setAcl": "full",
    "setErasureCodingPolicy": "full",
    "setOwner": "full",
    "setPermission": "full",
    "setQuota": "full",
    "setReplication": "full",
    "setSpaceQuota": "full",
    "setStoragePolicy": "full",
    "setTimes": "rw",
    "setXAttr": "rw",
    "startRollingUpgrade": "full",
    "truncate": "rw",
    "unsetErasureCodingPolicy": "full",
    "unsetStoragePolicy": "full",
}

# Actions I think are only relevant for the whole filesystem, and not specific to a file or directory
admin_actions := {
    "checkRestoreFailedStorage": "ro",
    "datanodeReport": "ro",
    "disableRestoreFailedStorage": "full",
    "enableRestoreFailedStorage": "full",
    "finalizeUpgrade": "rw",
    "getDatanodeStorageReport": "ro",
    "metaSave": "ro",
    "monitorHealth": "ro",
    "refreshNodes": "rw",
    "rollEditLog": "rw",
    "saveNamespace": "full",
    "setBalancerBandwidth": "rw",
    "slowDataNodesReport": "ro",
    "transitionToActive": "full",
    "transitionToObserver": "full",
    "transitionToStandby": "full",
}

groups_for_user := {"admin": ["admins"], "alice": ["developers"], "bob": []}

acls := [
    {
        "identity": "group:admins",
        "action": "full",
        "resource": "hdfs:dir:/",
    },
    {
        "identity": "group:developers",
        "action": "rw",
        "resource": "hdfs:dir:/developers/",
    },
    {
        "identity": "group:developers",
        "action": "ro",
        "resource": "hdfs:dir:/developers-ro/",
    },
    {
        "identity": "user:alice",
        "action": "rw",
        "resource": "hdfs:dir:/alice/",
    },
    {
        "identity": "user:bob",
        "action": "rw",
        "resource": "hdfs:dir:/bob/",
    },
    {
        "identity": "user:bob",
        "action": "ro",
        "resource": "hdfs:dir:/developers/",
    },
    {
        "identity": "user:bob",
        "action": "rw",
        "resource": "hdfs:file:/developers/file-from-bob",
    },
]
