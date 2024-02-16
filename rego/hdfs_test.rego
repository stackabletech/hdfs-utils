package hdfs

import rego.v1

test_admin_access_to_slash if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "admin"
        },
        "path": "/top-level",
        "operationName": "setErasureCodingPolicy",
    }
}

test_admin_access_to_alice if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "admin"
        },
        "path": "/alice/file",
        "operationName": "create",
    }
}


test_admin_access_to_alice_nested_file if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "admin"
        },
        "path": "/alice/nested/file",
        "operationName": "create",
    }
}

test_admin_access_to_developers if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "admin"
        },
        "path": "/developers/file",
        "operationName": "create",
    }
}



test_alice_access_to_alice_folder if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "alice"
        },
        "path": "/alice",
        "operationName": "create",
    }
}

test_alice_access_to_alice if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "alice"
        },
        "path": "/alice/file",
        "operationName": "create",
    }
}

test_alice_no_access_to_bob if {
    not allow with input as {
        "callerUgi": {
            "shortUserName": "alice"
        },
        "path": "/bob/file",
        "operationName": "open",
    }
}

test_alice_access_to_developers if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "alice"
        },
        "path": "/developers/file",
        "operationName": "create",
    }
}





test_bob_no_access_to_alice if {
    not allow with input as {
        "callerUgi": {
            "shortUserName": "bob"
        },
        "path": "/alice/file",
        "operationName": "open",
    }
}

test_bob_access_to_bob if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "bob"
        },
        "path": "/bob/file",
        "operationName": "create",
    }
}

test_bob_ro_access_to_developers if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "bob"
        },
        "path": "/developers/file",
        "operationName": "open",
    }
}

test_bob_no_rw_access_to_developers if {
    not allow with input as {
        "callerUgi": {
            "shortUserName": "bob"
        },
        "path": "/developers/file",
        "operationName": "create",
    }
}

test_bob_rw_access_to_developers_special_file if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "bob"
        },
        "path": "/developers/file-from-bob",
        "operationName": "create",
    }
}
