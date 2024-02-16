# Stackable Apache Hadoop utilities

[Stackable Data Platform](https://stackable.tech/) | [Platform Docs](https://docs.stackable.tech/) | [Discussions](https://github.com/orgs/stackabletech/discussions) | [Discord](https://discord.gg/7kZ3BNnCAF)

This project contains multiple plugins for Apache Hadoop, which are intended to be used with the [Stackable Data Platform](https://stackable.tech)

1. OpenPolicyAgent (OPA) authorizer: For every action performed in HDFS an OPA instance is asked if the user is allowed to perform the action.
2. GroupMapper: It can look up group memberships for users from an OPA instance.
3. Not (yet?) in this repository is a [TopologyProvider](https://github.com/stackabletech/hdfs-topology-provider/).

## Installation
Run `mvn package` and put the resulting `target/hdfs-utils-*.jar` file on your HDFS classpath.
The easiest way to achieve this is to put it in the directory `/stackable/hadoop/share/hadoop/tools/lib/`.
The Stackable HDFS already takes care of this, you don't need to do anything in this case.

## OPA authorizer

> [!IMPORTANT]
> The authorizer only works when used by an HDFS version that includes fixes from https://github.com/apache/hadoop/pull/6553.
> Stackable HDFS versions starting with `3.3.4` already contain this patch.

### Configuration

- Set `dfs.namenode.inode.attributes.provider.class` in `hdfs-site.xml` to `tech.stackable.hadoop.StackableAuthorizer`
- Set `hadoop.security.authorization.opa.policy.url` in `core-site.xml` to the HTTP endpoint of your OPA rego rule, e.g. `http://opa.default.svc.cluster.local:8081/v1/data/hdfs/allow`

### API

For every action a request similar to the following is sent to OPA:

<details>
<summary>Example request</summary>

```json
{
  "input": {
    "fsOwner": "nn",
    "supergroup": "supergroup",
    "callerUgi": {
      "realUser": null,
      "userName": "alice/test-hdfs-permissions.default.svc.cluster.local@CLUSTER.LOCAL",
      "shortUserName": "alice",
      "primaryGroup": "developers",
      "groups": [
        "developers"
      ],
      "authenticationMethod": "KERBEROS",
      "realAuthenticationMethod": "KERBEROS"
    },
    "inodeAttrs": [
      {
        "parent": null,
        "id": 16385,
        "modificationTime": 1707990801081,
        "accessTime": 0,
        "next": null,
        "features": [
          {
            "spaceConsumed": {
              "nameSpace": 7,
              "storageSpace": 1338,
              "typeSpaces": {}
            },
            "spaceAllowed": {
              "nameSpace": 9223372036854775807,
              "storageSpace": -1,
              "typeSpaces": {}
            }
          },
          {
            "diffs": {
              "last": null,
              "lastSnapshotId": 2147483646
            },
            "snapshotQuota": 0,
            "numSnapshots": 0,
            "snapshotList": [],
            "lastSnapshotId": 2147483646
          }
        ],
        "storagePolicyID": 0,
        "directorySnapshottableFeature": {
          "diffs": {
            "last": null,
            "lastSnapshotId": 2147483646
          },
          "snapshotQuota": 0,
          "numSnapshots": 0,
          "snapshotList": [],
          "lastSnapshotId": 2147483646
        },
        "directoryWithQuotaFeature": {
          "spaceConsumed": {
            "nameSpace": 7,
            "storageSpace": 1338,
            "typeSpaces": {}
          },
          "spaceAllowed": {
            "nameSpace": 9223372036854775807,
            "storageSpace": -1,
            "typeSpaces": {}
          }
        },
        "directoryWithSnapshotFeature": {
          "diffs": {
            "last": null,
            "lastSnapshotId": 2147483646
          },
          "snapshotQuota": 0,
          "numSnapshots": 0,
          "snapshotList": [],
          "lastSnapshotId": 2147483646
        },
        "quotaCounts": {
          "nameSpace": 9223372036854775807,
          "storageSpace": -1,
          "typeSpaces": {}
        },
        "localStoragePolicyID": 0,
        "diffs": {
          "last": null,
          "lastSnapshotId": 2147483646
        },
        "localNameBytes": "",
        "xattrFeature": null,
        "groupName": "supergroup",
        "fsPermission": {
          "stickyBit": false,
          "userAction": "ALL",
          "groupAction": "READ_EXECUTE",
          "otherAction": "READ_EXECUTE",
          "masked": null,
          "unmasked": null,
          "aclBit": false,
          "encryptedBit": false,
          "erasureCodedBit": false
        },
        "aclFeature": null,
        "fsPermissionShort": 493,
        "permissionLong": 1099511693805,
        "userName": "nn",
        "localName": "",
        "key": "",
        "fullPathName": "/",
        "pathComponents": [
          ""
        ],
        "objectString": "INodeDirectory@3ae77112",
        "parentString": "parent=null",
        "parentReference": null
      },
      {
        "parent": {
          "parent": null,
          "id": 16385,
          "modificationTime": 1707990801081,
          "accessTime": 0,
          "next": null,
          "features": [
            {
              "spaceConsumed": {
                "nameSpace": 7,
                "storageSpace": 1338,
                "typeSpaces": {}
              },
              "spaceAllowed": {
                "nameSpace": 9223372036854775807,
                "storageSpace": -1,
                "typeSpaces": {}
              }
            },
            {
              "diffs": {
                "last": null,
                "lastSnapshotId": 2147483646
              },
              "snapshotQuota": 0,
              "numSnapshots": 0,
              "snapshotList": [],
              "lastSnapshotId": 2147483646
            }
          ],
          "storagePolicyID": 0,
          "directorySnapshottableFeature": {
            "diffs": {
              "last": null,
              "lastSnapshotId": 2147483646
            },
            "snapshotQuota": 0,
            "numSnapshots": 0,
            "snapshotList": [],
            "lastSnapshotId": 2147483646
          },
          "directoryWithQuotaFeature": {
            "spaceConsumed": {
              "nameSpace": 7,
              "storageSpace": 1338,
              "typeSpaces": {}
            },
            "spaceAllowed": {
              "nameSpace": 9223372036854775807,
              "storageSpace": -1,
              "typeSpaces": {}
            }
          },
          "directoryWithSnapshotFeature": {
            "diffs": {
              "last": null,
              "lastSnapshotId": 2147483646
            },
            "snapshotQuota": 0,
            "numSnapshots": 0,
            "snapshotList": [],
            "lastSnapshotId": 2147483646
          },
          "quotaCounts": {
            "nameSpace": 9223372036854775807,
            "storageSpace": -1,
            "typeSpaces": {}
          },
          "localStoragePolicyID": 0,
          "diffs": {
            "last": null,
            "lastSnapshotId": 2147483646
          },
          "localNameBytes": "",
          "xattrFeature": null,
          "groupName": "supergroup",
          "fsPermission": {
            "stickyBit": false,
            "userAction": "ALL",
            "groupAction": "READ_EXECUTE",
            "otherAction": "READ_EXECUTE",
            "masked": null,
            "unmasked": null,
            "aclBit": false,
            "encryptedBit": false,
            "erasureCodedBit": false
          },
          "aclFeature": null,
          "fsPermissionShort": 493,
          "permissionLong": 1099511693805,
          "userName": "nn",
          "localName": "",
          "key": "",
          "fullPathName": "/",
          "pathComponents": [
            ""
          ],
          "objectString": "INodeDirectory@3ae77112",
          "parentString": "parent=null",
          "parentReference": null
        },
        "id": 16389,
        "modificationTime": 1707990801081,
        "accessTime": 0,
        "next": null,
        "features": [],
        "storagePolicyID": 0,
        "directorySnapshottableFeature": null,
        "directoryWithQuotaFeature": null,
        "directoryWithSnapshotFeature": null,
        "quotaCounts": {
          "nameSpace": -1,
          "storageSpace": -1,
          "typeSpaces": {}
        },
        "localStoragePolicyID": 0,
        "diffs": null,
        "localNameBytes": "ZGV2ZWxvcGVycy1ybw==",
        "xattrFeature": null,
        "groupName": "supergroup",
        "fsPermission": {
          "stickyBit": false,
          "userAction": "ALL",
          "groupAction": "READ_EXECUTE",
          "otherAction": "READ_EXECUTE",
          "masked": null,
          "unmasked": null,
          "aclBit": false,
          "encryptedBit": false,
          "erasureCodedBit": false
        },
        "aclFeature": null,
        "fsPermissionShort": 493,
        "permissionLong": 2199023321581,
        "userName": "admin",
        "localName": "developers-ro",
        "key": "ZGV2ZWxvcGVycy1ybw==",
        "fullPathName": "/developers-ro",
        "pathComponents": [
          "",
          "ZGV2ZWxvcGVycy1ybw=="
        ],
        "objectString": "INodeDirectory@1df11410",
        "parentString": "parentDir=/",
        "parentReference": null
      },
      null
    ],
    "inodes": [
      {
        "parent": null,
        "id": 16385,
        "modificationTime": 1707990801081,
        "accessTime": 0,
        "next": null,
        "features": [
          {
            "spaceConsumed": {
              "nameSpace": 7,
              "storageSpace": 1338,
              "typeSpaces": {}
            },
            "spaceAllowed": {
              "nameSpace": 9223372036854775807,
              "storageSpace": -1,
              "typeSpaces": {}
            }
          },
          {
            "diffs": {
              "last": null,
              "lastSnapshotId": 2147483646
            },
            "snapshotQuota": 0,
            "numSnapshots": 0,
            "snapshotList": [],
            "lastSnapshotId": 2147483646
          }
        ],
        "storagePolicyID": 0,
        "directorySnapshottableFeature": {
          "diffs": {
            "last": null,
            "lastSnapshotId": 2147483646
          },
          "snapshotQuota": 0,
          "numSnapshots": 0,
          "snapshotList": [],
          "lastSnapshotId": 2147483646
        },
        "directoryWithQuotaFeature": {
          "spaceConsumed": {
            "nameSpace": 7,
            "storageSpace": 1338,
            "typeSpaces": {}
          },
          "spaceAllowed": {
            "nameSpace": 9223372036854775807,
            "storageSpace": -1,
            "typeSpaces": {}
          }
        },
        "directoryWithSnapshotFeature": {
          "diffs": {
            "last": null,
            "lastSnapshotId": 2147483646
          },
          "snapshotQuota": 0,
          "numSnapshots": 0,
          "snapshotList": [],
          "lastSnapshotId": 2147483646
        },
        "quotaCounts": {
          "nameSpace": 9223372036854775807,
          "storageSpace": -1,
          "typeSpaces": {}
        },
        "localStoragePolicyID": 0,
        "diffs": {
          "last": null,
          "lastSnapshotId": 2147483646
        },
        "localNameBytes": "",
        "xattrFeature": null,
        "groupName": "supergroup",
        "fsPermission": {
          "stickyBit": false,
          "userAction": "ALL",
          "groupAction": "READ_EXECUTE",
          "otherAction": "READ_EXECUTE",
          "masked": null,
          "unmasked": null,
          "aclBit": false,
          "encryptedBit": false,
          "erasureCodedBit": false
        },
        "aclFeature": null,
        "fsPermissionShort": 493,
        "permissionLong": 1099511693805,
        "userName": "nn",
        "localName": "",
        "key": "",
        "fullPathName": "/",
        "pathComponents": [
          ""
        ],
        "objectString": "INodeDirectory@3ae77112",
        "parentString": "parent=null",
        "parentReference": null
      },
      {
        "parent": {
          "parent": null,
          "id": 16385,
          "modificationTime": 1707990801081,
          "accessTime": 0,
          "next": null,
          "features": [
            {
              "spaceConsumed": {
                "nameSpace": 7,
                "storageSpace": 1338,
                "typeSpaces": {}
              },
              "spaceAllowed": {
                "nameSpace": 9223372036854775807,
                "storageSpace": -1,
                "typeSpaces": {}
              }
            },
            {
              "diffs": {
                "last": null,
                "lastSnapshotId": 2147483646
              },
              "snapshotQuota": 0,
              "numSnapshots": 0,
              "snapshotList": [],
              "lastSnapshotId": 2147483646
            }
          ],
          "storagePolicyID": 0,
          "directorySnapshottableFeature": {
            "diffs": {
              "last": null,
              "lastSnapshotId": 2147483646
            },
            "snapshotQuota": 0,
            "numSnapshots": 0,
            "snapshotList": [],
            "lastSnapshotId": 2147483646
          },
          "directoryWithQuotaFeature": {
            "spaceConsumed": {
              "nameSpace": 7,
              "storageSpace": 1338,
              "typeSpaces": {}
            },
            "spaceAllowed": {
              "nameSpace": 9223372036854775807,
              "storageSpace": -1,
              "typeSpaces": {}
            }
          },
          "directoryWithSnapshotFeature": {
            "diffs": {
              "last": null,
              "lastSnapshotId": 2147483646
            },
            "snapshotQuota": 0,
            "numSnapshots": 0,
            "snapshotList": [],
            "lastSnapshotId": 2147483646
          },
          "quotaCounts": {
            "nameSpace": 9223372036854775807,
            "storageSpace": -1,
            "typeSpaces": {}
          },
          "localStoragePolicyID": 0,
          "diffs": {
            "last": null,
            "lastSnapshotId": 2147483646
          },
          "localNameBytes": "",
          "xattrFeature": null,
          "groupName": "supergroup",
          "fsPermission": {
            "stickyBit": false,
            "userAction": "ALL",
            "groupAction": "READ_EXECUTE",
            "otherAction": "READ_EXECUTE",
            "masked": null,
            "unmasked": null,
            "aclBit": false,
            "encryptedBit": false,
            "erasureCodedBit": false
          },
          "aclFeature": null,
          "fsPermissionShort": 493,
          "permissionLong": 1099511693805,
          "userName": "nn",
          "localName": "",
          "key": "",
          "fullPathName": "/",
          "pathComponents": [
            ""
          ],
          "objectString": "INodeDirectory@3ae77112",
          "parentString": "parent=null",
          "parentReference": null
        },
        "id": 16389,
        "modificationTime": 1707990801081,
        "accessTime": 0,
        "next": null,
        "features": [],
        "storagePolicyID": 0,
        "directorySnapshottableFeature": null,
        "directoryWithQuotaFeature": null,
        "directoryWithSnapshotFeature": null,
        "quotaCounts": {
          "nameSpace": -1,
          "storageSpace": -1,
          "typeSpaces": {}
        },
        "localStoragePolicyID": 0,
        "diffs": null,
        "localNameBytes": "ZGV2ZWxvcGVycy1ybw==",
        "xattrFeature": null,
        "groupName": "supergroup",
        "fsPermission": {
          "stickyBit": false,
          "userAction": "ALL",
          "groupAction": "READ_EXECUTE",
          "otherAction": "READ_EXECUTE",
          "masked": null,
          "unmasked": null,
          "aclBit": false,
          "encryptedBit": false,
          "erasureCodedBit": false
        },
        "aclFeature": null,
        "fsPermissionShort": 493,
        "permissionLong": 2199023321581,
        "userName": "admin",
        "localName": "developers-ro",
        "key": "ZGV2ZWxvcGVycy1ybw==",
        "fullPathName": "/developers-ro",
        "pathComponents": [
          "",
          "ZGV2ZWxvcGVycy1ybw=="
        ],
        "objectString": "INodeDirectory@1df11410",
        "parentString": "parentDir=/",
        "parentReference": null
      },
      null
    ],
    "pathByNameArr": [
      "",
      "ZGV2ZWxvcGVycy1ybw==",
      "aG9zdHMuX0NPUFlJTkdf"
    ],
    "snapshotId": 2147483646,
    "path": "/developers-ro/hosts._COPYING_",
    "ancestorIndex": 1,
    "doCheckOwner": false,
    "ancestorAccess": null,
    "parentAccess": null,
    "access": null,
    "subAccess": null,
    "ignoreEmptyDir": false,
    "operationName": "getfileinfo",
    "callerContext": {
      "context": "CLI",
      "signature": null
    }
  }
}
```

</details>

## Group mapper

Despites having the OPA authorizer described above there are a few use-cases to have a group mapper as well.

1. Correctly showing group information in HDFS, e.g. for file ownership.
2. Only use the group mapper without the OAP authorizer

Hadoop offers a few default group providers, such as:

* LDAP
* Linux user group (usually provided by SSSD, Centrify or similar tools)

Hadoop exposes an [interface](https://github.com/apache/hadoop/blob/rel/release-3.3.6/hadoop-common-project/hadoop-common/src/main/java/org/apache/hadoop/security/GroupMappingServiceProvider.java) that users can implement to extend these [group mappings](https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-common/GroupsMapping.html). The Stackable Group Mapper does this to look up user groups from OPA.

### Configuration

- Set `hadoop.security.group.mapping` in `core-site.xml` to `tech.stackable.hadoop.StackableGroupMapper`
- Set `hadoop.security.group.mapping.opa.policy.url` in `core-site.xml` to the HTTP endpoint of your OPA rego rule, e.g. `http://opa.default.svc.cluster.local:8081/v1/data/hdfs/groups`
- Make sure to not have set `hadoop.user.group.static.mapping.overrides` in `core-site.xml`, as this clashes with the information the group mapper provides.

### API

The group mapper sends the following query to OPA:

```json
{
  "input": {
    "username": "alice"
  }
}
```

OPA needs to respond with the list of groups as follows:

```json
{
  "result": [
    "admin",
    "developers"
  ]
}
```

### Testing

CRDs for spinning up test infrastructure are provided in `test/stack`. The Tiltfile will deploy these resources, build and copy the mapper to the docker image, and re-deploy the image to the running HdfsCluster.

The group mapping can be verified by shelling into the namenode, requesting a kerberos ticket and then calling `hdfs groups` for the namenode user:

```shell
klist -k /stackable/kerberos/keytab
kinit -kt /stackable/kerberos/keytab nn/simple-hdfs.default.svc.cluster.local@CLUSTER.LOCAL
klist

# N.B. hadoop will replace the realm placeholder with the env-var automatically but this must be done manually if shelling into the container:
export KERBEROS_REALM=$(grep -oP 'default_realm = \K.*' /stackable/kerberos/krb5.conf)
cat /stackable/config/namenode/core-site.xml | sed -e 's/${env.KERBEROS_REALM}/'"$KERBEROS_REALM/g" > /stackable/config/namenode/core-site2.xml
mv /stackable/config/namenode/core-site2.xml /stackable/config/namenode/core-site.xml

bin/hdfs groups
```

The last command will yield something like this:

```shell
nn/simple-hdfs.default.svc.cluster.local@CLUSTER.LOCAL : admin superuser
```

and the Hadoop logs will show that the lookup has taken place:

```text
- Calling StackableGroupMapper.getGroups for user [nn]
- Opa response [{"result":{"groups":{"groups":["admin","superuser"]},"users_by_name":{"alice":{"customAttributes":{},"groups":["/superset-admin"],"id":"af07f12c-1234-40a7-93e0-874537bdf3f5","username":"alice"},"bob":{"customAttributes":{},"groups":["/admin"],"id":"af07f12c-2345-40a7-93e0-874537bdf3f5","username":"bob"},"nn":{"customAttributes":{},"groups":["/admin","/superuser"],"id":"af07f12c-7890-40a7-93e0-874537bdf3f5","username":"nn"},"stackable":{"customAttributes":{},"groups":["/admin","/superuser"],"id":"af07f12c-3456-40a7-93e0-874537bdf3f5","username":"stackable"}}}}
- Groups for [nn]: [[admin, superuser]]
```

