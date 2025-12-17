# Testing

## Install operators

```bash
stackablectl op in secret commons listener secret zookeeper hdfs spark-k8s
```

## Run scripts in the `default` namespace

```bash
kubectl apply -f ./hdfs-utils/test/topology-provider/stack/01-install-krb5-kdc.yaml
kubectl apply -f ./hdfs-utils/test/topology-provider/stack/02-create-kerberos-secretclass.yaml
kubectl apply -f ./hdfs-utils/test/topology-provider/stack/03-hdfs.yaml
kubectl apply -f ./hdfs-utils/test/topology-provider/stack/04-spark.yaml
kubectl apply -f ./hdfs-utils/test/topology-provider/stack/05-access-hdfs.yaml
```
