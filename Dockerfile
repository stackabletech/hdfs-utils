FROM docker.stackable.tech/stackable/hadoop:3.4.0-stackable0.0.0-dev

# Remove existing hdfs-utils jars, so we can ship our custom one
RUN rm -f /stackable/hadoop/share/hadoop/common/lib/hdfs-utils-*.jar
RUN rm -f /stackable/hadoop/share/hadoop/tools/lib/hdfs-utils-*.jar

COPY --chown=stackable:stackable ./hdfs-utils-*.jar /stackable/hadoop/share/hadoop/common/lib/
COPY --chown=stackable:stackable ./bom.json /stackable/hadoop/share/hadoop/common/lib/hdfs-utils.cdx.json
