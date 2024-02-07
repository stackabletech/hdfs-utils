package hdfs

import rego.v1

test_admin_access_to_slash if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "admin"
        },
        "path": "/top-level",
        "operationName": "delete",
    }
}

test_admin_access_to_alice if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "admin"
        },
        "path": "/alice/file",
        "operationName": "delete",
    }
}


test_admin_access_to_alice_nested_file if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "admin"
        },
        "path": "/alice/nested/file",
        "operationName": "delete",
    }
}

test_admin_access_to_developers if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "admin"
        },
        "path": "/developers/file",
        "operationName": "getfileinfo",
    }
}



test_alice_access_to_alice_folder if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "alice"
        },
        "path": "/alice",
        "operationName": "getfileinfo",
    }
}

test_alice_access_to_alice if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "alice"
        },
        "path": "/alice/file",
        "operationName": "delete",
    }
}

test_alice_no_access_to_bob if {
    not allow with input as {
        "callerUgi": {
            "shortUserName": "alice"
        },
        "path": "/bob/file",
        "operationName": "delete",
    }
}

test_alice_access_to_developers if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "alice"
        },
        "path": "/developers/file",
        "operationName": "delete",
    }
}





test_bob_no_access_to_alice if {
    not allow with input as {
        "callerUgi": {
            "shortUserName": "bob"
        },
        "path": "/alice/file",
        "operationName": "delete",
    }
}

test_bob_access_to_bob if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "bob"
        },
        "path": "/bob/file",
        "operationName": "delete",
    }
}

test_bob_ro_access_to_developers if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "bob"
        },
        "path": "/developers/file",
        "operationName": "getfileinfo",
    }
}

test_bob_no_rw_access_to_developers if {
    not allow with input as {
        "callerUgi": {
            "shortUserName": "bob"
        },
        "path": "/developers/file",
        "operationName": "delete",
    }
}

test_bob_rw_access_to_developers_special_file if {
    allow with input as {
        "callerUgi": {
            "shortUserName": "bob"
        },
        "path": "/developers/file-from-bob",
        "operationName": "listStatus", # FIXME: Change to operation that needs rw action
    }
}
