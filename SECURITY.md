# Security Policy

## Supported versions

Security fixes are applied to the latest release line.

## Reporting a vulnerability

Please do not open a public issue for security vulnerabilities.

Report them privately by email to the maintainer listed in the repository
profile, or use GitHub's private vulnerability reporting if it is enabled for
the repository.

Include:

- Affected version
- Description of the issue
- Reproduction steps or proof of concept
- Potential impact

We will acknowledge the report as soon as possible and coordinate a fix and
disclosure.

## Scope

SpringAnalyzer parses untrusted Java source code but does not execute it. The
main security considerations are:

- Resource exhaustion when scanning very large repositories
- Malicious or malformed source files
- Supply-chain integrity of released JARs

Release artifacts should be verified against the published SHA-256 checksums.
