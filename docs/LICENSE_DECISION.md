# License Decision Required

The repository has a configured GitHub remote, but repository visibility and the owner's intended
distribution policy cannot be established from the local checkout alone. Before distributing the
APK or source, the owner must decide whether the project is private/public and whether APKs, source,
or both will be distributed.

Common choices are:

- Proprietary / All rights reserved;
- MIT;
- Apache-2.0.

No `LICENSE` is created until the owner selects one. Dependency notices must be generated from the
actual resolved dependency metadata and licenses, not assumptions.
