FROM docker.stackable.tech/stackable/hadoop:3.3.6-stackable0.0.0-dev@sha256:de365f020d4c677ef7a3a0d6adc31b0b1e0676c6cbebda4c7149030e0317e295

COPY --chown=stackable:stackable ./hdfs-utils-*.jar /stackable/hadoop/share/hadoop/tools/lib/
