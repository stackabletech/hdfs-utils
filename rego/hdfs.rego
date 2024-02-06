package hdfs

import rego.v1

default allow = false

allow if {
    some acl in acls
    matches_identity(input.callerUgi.shortUserName, acl.identity)
    matches_resource(input.path, acl.resource)
    action_sufficient_for_operation(acl.action, input.operationName)
}

# Identity mentions the user explicitly
matches_identity(user, identity) if {
    identity == concat("", ["user:", user])
}

# Identity mentions group the user is part of
matches_identity(user, identity) if {
    some group in groups[user]
    identity == concat("", ["group:", group])
}


# Resource mentions the file explicitly
matches_resource(file, resource) if {
    resource == concat("", ["hdfs:file:", file])
}

# Resource mentions a folder higher up the tree, which will will grant access recursively
matches_resource(file, resource) if {
    startswith(resource, "hdfs:dir:/")
    # dirs need to have a trailing slash
    endswith(resource, "/")
    startswith(file, trim_prefix(resource, "hdfs:dir:"))
}

action_sufficient_for_operation(action, operation) if {
    action_hierarchy[action][_] == action_for_operation[operation]
}

action_hierarchy := {
    "full": ["full", "rw","ro"],
    "rw": ["rw", "ro"],
    "ro": ["ro"],
}

action_for_operation := {
    "getfileinfo": "ro",
    "listStatus": "ro",
    "mkdirs": "full", # TODO check if this is ok
    "delete": "full",
    "rename": "full", # FIXME: Should check source *and* target
}

groups := {"admin": ["admins"], "alice": ["developers"], "bob": [], "HTTP": ["admins"]}

acls := [
    {
        "identity": "group:admins",
        "action": "full",
        "resource": "hdfs:dir:/",
    },
    {
        "identity": "group:developers",
        "action": "full",
        "resource": "hdfs:dir:/developers/",
    },
    {
        "identity": "user:alice",
        "action": "full",
        "resource": "hdfs:dir:/alice/",
    },
    {
        "identity": "user:bob",
        "action": "full",
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
