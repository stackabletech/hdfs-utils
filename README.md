# Stackable Apache Hadoop utilities

[Stackable Data Platform](https://stackable.tech/) | [Platform Docs](https://docs.stackable.tech/) | [Discussions](https://github.com/orgs/stackabletech/discussions) | [Discord](https://discord.gg/7kZ3BNnCAF)

This project contains multiple plugins for Apache Hadoop, which are intended to be used with the [Stackable Data Platform](https://stackable.tech)

1. OpenPolicyAgent (OPA) authorizer: For every action performed in HDFS an OPA instance is asked if the user is allowed to perform the action.
2. GroupMapper: It can look up group memberships for users from an OPA instance.
3. TopologyProvider: It is used to feed information from Kubernetes into the HDFS rack awareness functionality.

## Installation

Currently you can compile hdfs-utils against Hadoop 3.3.4, 3.3.6 or 3.4.0. You need to specify the version by activating either the `hadoop-3.3.4`, `hadoop-3.3.6` or the `hadoop-3.4.0` profile below.

Run `mvn clean package -P hadoop-3.4.0` and put the resulting `target/hdfs-utils-*.jar` file on your HDFS classpath.
The easiest way to achieve this is to put it in the directory `/stackable/hadoop/share/hadoop/tools/lib/`.
The Stackable HDFS already takes care of this, you don't need to do anything in this case.

## OPA authorizer

> [!IMPORTANT]
> The authorizer only works when used by an HDFS version that includes fixes from https://github.com/apache/hadoop/pull/6553.
> Stackable HDFS versions starting with `3.3.4` already contain this patch.

### Configuration

- Set `dfs.namenode.inode.attributes.provider.class` in `hdfs-site.xml` to `tech.stackable.hadoop.StackableAuthorizer`
- Set `hadoop.security.authorization.opa.policy.url` in `core-site.xml` to the HTTP endpoint of your OPA rego rule, e.g. `http://opa.default.svc.cluster.local:8081/v1/data/hdfs/allow`
- The property `hadoop.security.authorization.opa.extended-requests` (defaults to `false`) controls if all fields (`true`) should be sent to OPA or only a subset
  Sending all fields degrades the performance, but allows for more advanced authorization.

### API

By default for every HDFS action a request similar to the following is sent to OPA:

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
    "snapshotId": 2147483646,
    "path": "/developers-ro/hosts._COPYING_",
    "ancestorIndex": 1,
    "doCheckOwner": false,
    "ignoreEmptyDir": false,
    "operationName": "getfileinfo",
    "callerContext": {
      "context": "CLI",
      "signature": null
    }
  }
}
```

The contained details should be sufficient for most use-cases.
However, if you need access to all the provided information from the `INodeAttributeProvider.AccessControlEnforcer` interface, you can instruct hdfs-utils to send all fields by setting `hadoop.security.authorization.opa.extended-requests` to `true`.
However, please note that this results in very big JSON objects being send from HDFS to OPA, so please keep an eye on performance degradations.

The following example provides an extend request sending all available fields:

<details>
<summary>Example extended request</summary>

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

## Network Topology Provider

Hadoop supports a concept called [rack awareness](https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-common/RackAwareness.html) (lately also often called topology awareness).

Historically this has been used to distinguish racks, datacenters and the like and often been read from a manually updated topology file or similar solutions.

In Kubernetes, the most commonly used mechanism for topology awareness are labels - mostly labels set on the Kubernetes nodes.
The most prevalent example for this is the node label [topology.kubernetes.io/zone](https://kubernetes.io/docs/reference/labels-annotations-taints/#topologykubernetesiozone) which often refers to availability zones in cloud providers or similar things.

The purpose of this tool is to feed information from Kubernetes into the HDFS rack awareness functionality.
In order to do this, it implements the Hadoop interface `org.apache.hadoop.net.DNSToSwitchMapping` which then allows this tool to be configured on the NameNode via the parameter `net.topology.node.switch.mapping.impl`.

The topology provider watches all HDFS pods deployed by Stackable and Kubernetes nodes and keeps an in memory cache of the current state of these objects.
From this state store the tool can then calculate rack IDs for nodes that HDFS asks for without needing to talk to the api-server and incurring an extra network round-trip.

Results are cached for a configurable amount of time and served from the cache if present.

In a Kubernetes environment it is likely that the majority of writes will not come from the DataNodes themselves, but rather from other processes such as Spark executors writing data to HDFS. The NameNode passes these on to the topology provider to request the rack ID i.e. it provides the IP addresses of whichever pods are doing the writing. If a datanode resides on the same Kubernetes node as one of these pods, then this datanode is used for label resolution for that pod.

## Configuration

Configuration of the tool happens via environment variables, as shown below:

### TOPOLOGY_LABELS

A semicolon separated list of labels that should be used to build a rack id for a datanode.

A label is specified as `[node|pod]:<labelname>`

Some examples:

|            Definition            |                                             Resolved to                                              |
|----------------------------------|------------------------------------------------------------------------------------------------------|
| node:topology.kubernetes.io/zone | The value of the label 'topology.kubernetes.io/zone' on the node to which the pod has been assigned. |
| pod:app.kubernetes.io/role-group | The value of the label 'app.kubernetes.io/role-group' on the datanode pod.                           |

Multiple levels of labels can be combined (up to MAX_TOPOLOGY_LABELS levels) by separating them with a semicolon:

So for example `node:topology.kubernetes.io/zone;pod:app.kubernetes.io/role-group` would resolve to `/<value of label topology.kubernetes.io/zone on the node>/<value of label app.kubernetes.io/role-group on the pod>`.

### TOPOLOGY_CACHE_EXPIRATION_SECONDS

Default: 5 Minutes

The default time for which rack ids are cached, HDFS can influence caching, as the provider offers methods to invalidate the cache, so this is not a totally _reliable_ value, as there are external factors not under the control of this tool.

### MAX_TOPOLOGY_LEVELS

Default: 2

The maximum number of levels that can be specified to build a rack id from.

While this can be changed, HDFS probably only supports a maximum of two levels, so it is not recommended to change the default for this setting.

## Testing

There are currently no unit tests.

CRDs for spinning up test infrastructure are provided in `test/stack`.

The actual testing for this happens in the integration tests of the HDFS operator.
