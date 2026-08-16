# Contributing to SoulFire

Thank you for contributing to SoulFire. Follow these code-style rules before you submit a pull request.

## Code style

This project does not have a strict code style.
However, we will only accept pull requests that use the format specified in `.editorconfig`.
Everything enforced by Spotless is also required.

### `var` keyword

SoulFire uses `var` instead of explicit local variable types. For example, replace
`Map<String, String> map = new HashMap<>()` with `var map = new HashMap<String, String>()`.

### IntelliJ inspections

You can also import the recommended inspections at `config/intellij_inspections.xml`
at `Settings -> Editor -> Inspections`.
