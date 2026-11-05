# Contributing

Thanks for your interest in SpringAnalyzer.

## Development setup

Requirements:

- Java 21+
- Git

Build and test:

```bash
./mvnw clean verify
```

Run the scanner against the fixture:

```bash
./mvnw -q package
java -jar target/spring-analyzer-1.0.0.jar src/test/resources/fixtures/demo
```

## Pull requests

- Keep changes focused and add tests for new behavior.
- Update `README.md` and `CHANGELOG.md` when user-facing behavior changes.
- Use English for code comments, log messages, tests, and documentation.
- Do not commit build artifacts (`target/`) or generated JARs.
- Follow the existing Java style: 4-space indentation, records for data
  carriers, and small focused classes.

## Commit messages

Use short imperative messages, for example:

```text
Add multi-path endpoint support
Fix constant resolution across packages
```

## Reporting bugs

Open an issue with:

- Java version
- Operating system
- Command used
- A minimal source snippet or repository that reproduces the problem
- Actual and expected output

For security issues, follow [SECURITY.md](SECURITY.md) instead of opening a
public issue.
