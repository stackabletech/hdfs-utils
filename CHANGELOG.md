# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Changed

- Updates various dependencies and does a full spotless run. This will now require JDK 17 or later to build (required by later error-prone versions), the build target is still Java 11 [#51

[#51]: https://github.com/stackabletech/hdfs-utils/pull/51

## [0.3.0] - 2024-07-04

### Added

- Add topology-provider from https://github.com/stackabletech/hdfs-topology-provider ([#28]).
- Introduce maven profile to build against Hadoop 3.3.4, 3.3.6 or 3.4.0 ([#29]).

[#28]: https://github.com/stackabletech/hdfs-utils/pull/28
[#29]: https://github.com/stackabletech/hdfs-utils/pull/29
