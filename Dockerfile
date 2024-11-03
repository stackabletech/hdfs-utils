FROM docker.stackable.tech/stackable/hadoop:3.3.6-stackable0.0.0-dev

COPY --chown=stackable:stackable ./hdfs-utils-*.jar /stackable/hadoop/share/hadoop/tools/lib/
COPY --chown=stackable:stackable ./bom.json /stackable/hadoop/share/hadoop/tools/lib/hdfs-utils.cdx.json
