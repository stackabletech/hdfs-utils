FROM docker.stackable.tech/stackable/hadoop:3.3.6-stackable0.0.0-dev@sha256:62f25b541297fe6ad1557622a02e6b9a6068580887a604c5277492442d27f93b

COPY --chown=stackable:stackable ./hdfs-utils-*.jar /stackable/hadoop/share/hadoop/tools/lib/
COPY --chown=stackable:stackable ./bom.json /stackable/hadoop/share/hadoop/tools/lib/hdfs-utils.cdx.json
