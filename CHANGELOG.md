# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

## [0.4.0] - 2024-11-05

### Changed

- BREAKING: Only send a subset of the fields sufficient for most use-cases to OPA for performance reasons.
  The old behavior of sending all fields can be restored by setting `hadoop.security.authorization.opa.extended-requests`
  to `true` ([#49]).
- Performance fixes ([#50])
- Updates various dependencies and do a full spotless run. This will now require JDK 17 or later to build
  (required by later error-prone versions), the build target is still Java 11 [#51]
- Bump okio to 1.17.6 to get rid of CVE-2023-3635 ([#46])

### Fixed

- Set path to `/` when the operation `contentSummary` is called on `/`. Previously path was set to `null` ([#49]).

[#46]: https://github.com/stackabletech/hdfs-utils/pull/46
[#49]: https://github.com/stackabletech/hdfs-utils/pull/49
[#50]: https://github.com/stackabletech/hdfs-utils/pull/50
[#51]: https://github.com/stackabletech/hdfs-utils/pull/51

## [0.3.0] - 2024-07-04

### Added

- Add topology-provider from https://github.com/stackabletech/hdfs-topology-provider ([#28]).
- Introduce maven profile to build against Hadoop 3.3.4, 3.3.6 or 3.4.0 ([#29]).

[#28]: https://github.com/stackabletech/hdfs-utils/pull/28
[#29]: https://github.com/stackabletech/hdfs-utils/pull/29

