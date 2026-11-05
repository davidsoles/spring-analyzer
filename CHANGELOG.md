# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Planned

- `@EventListener` publisher/subscriber extraction
- Event relationship graph
- Scheduled task extraction

## [1.0.0] - 2026-11-04

### Added

- Initial SpringAnalyzer release.
- REST endpoint extraction for Spring MVC controllers.
- Support for `@RestController`, `@Controller`, `@ResponseBody`, and all standard
  mapping annotations.
- Support for composed annotations, constants, inheritance, interfaces, nested
  controllers, multiple paths, and multiple HTTP verbs.
- Table, JSON, and CSV output.
- Versioned JSON envelope (`schemaVersion`, `scannerVersion`, `endpoints`).
- CLI filters for tests, MVC view methods, and absolute paths.
- Maven build with shaded executable JAR.
