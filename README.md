# FCA4J

A Java toolkit for Formal Concept Analysis (FCA) and Relational Concept Analysis (RCA).

[![Build fat JAR](https://github.com/guti34/fca4j-project/actions/workflows/build-fat-jar.yml/badge.svg)](https://github.com/guti34/fca4j-project/actions/workflows/build-fat-jar.yml)
[![Maven Central](https://img.shields.io/maven-central/v/fr.lirmm.fca4j/fca4j-core.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/fr.lirmm.fca4j/fca4j-core)
[![License: BSD-3-Clause](https://img.shields.io/badge/license-BSD--3--Clause-blue.svg)](LICENCE.txt)

FCA4J builds concept lattices, AOC-posets, and implication bases from formal contexts, and extends this to families of related contexts through Relational Concept Analysis. It can be used as a command-line tool or embedded as a Java library, and it powers [FCA4J UI](https://github.com/guti34/fca4j-ui), the companion desktop application.

## Features

**Formal Concept Analysis**
- Concept lattice construction (`lattice`) — complete lattice (ADD_EXTENT) or top-most concepts (ICEBERG)
- AOC-poset construction (`aocposet`) — the sub-order restricted to attribute- and object-concepts
- Canonical implication basis — Duquenne-Guigues (`dg_basis`) and ordered direct D-Basis (`dbasis`)
- Context operations: `clarify`, `reduce`, `inspect`, `irreducible`

**Relational Concept Analysis**
- Conceptual structure families from relational context families (`rca`), with [RCAviz](http://rcaviz.lirmm.fr) export
- Family import (`family_import`)

**Data management**
- `binarize`, `convert`, `family` — format conversions across multiple formal-context representations

**Performance**
- Native JNI/CRoaring optimization for D-Basis computation (`fca4j-core-natif`), with a transparent pure-Java fallback when the native library isn't available for the platform
- Pluggable integer-set implementations (`BitSet`, `RoaringBitmap`, `PackedBitSet`, fastutil, Koloboke, HPPC, Trove) for performance tuning on large contexts

## Module architecture

| Module | Description |
|---|---|
| `fca4j-core` | Core FCA/RCA algorithms and data model |
| `fca4j-core-natif` | Native (JNI) optimizations for `fca4j-core`, with Java fallback |
| `fca4j-iset` | Integer-set implementations (`BitSet`, RoaringBitmap...) |
| `fca4j-iset-ext` | Additional set implementations (fastutil, Koloboke, HPPC, Trove, JOCL) |
| `fca4j-io` | Import/export for formal contexts and relational context families |
| `fca4j-command` | Command-line command definitions, shared by both CLI variants |
| `fca4j-app` | Full command-line interface (executable jar) |
| `fca4j-app-light` | Command-line interface without the extra set implementations, for a smaller jar |

## Getting started

### As a command-line tool

Download the latest build (`fca4j.jar`) from the [continuous release](https://github.com/guti34/fca4j-project/releases/download/continuous/fca4j.jar), then:

```bash
java -jar fca4j.jar <command> <input> [<output>] [options]
```

See the [Getting started guide](https://www.lirmm.fr/fca4j/Gettingstarted.html) for a full walkthrough, the [command reference](https://www.lirmm.fr/fca4j/Commands.html) for every command and option, and [Tips & Tricks](https://www.lirmm.fr/fca4j/Tipsandtricks.html) for algorithm and option choices not covered by the reference docs.

### As a Java library

```xml
<dependency>
    <groupId>fr.lirmm.fca4j</groupId>
    <artifactId>fca4j-core</artifactId>
    <version><!-- see Maven Central badge above --></version>
</dependency>
```

Add `fca4j-io` for import/export, or `fca4j-core-natif` for the native D-Basis optimization.

## Building from source

```bash
mvn clean install -DskipTests
```

Native optimizations (`fca4j-core-natif`) are only compiled when the `FCA4J_BUILD_NATIVE=true` environment variable is set; otherwise the module falls back to a pure-Java implementation, which is enough for development.

## Related projects

- [FCA4J UI](https://github.com/guti34/fca4j-ui) — companion JavaFX desktop application
- [Documentation site](https://www.lirmm.fr/fca4j) — full documentation, command reference, and tutorials
- [RCAviz](http://rcaviz.lirmm.fr) — RCA visualization tool
- [FCAvizir](https://fcavizir.lirmm.fr/) — visual exploration of FCA-generated implication rules
- [fca4j-benchmark](https://gite.lirmm.fr/gutierre/fca4j-benchmark) — benchmark datasets

## Support & Community

Questions, ideas, feedback, or anything you're unsure is a bug — all welcome on the [FCA4J Forum](https://github.com/guti34/fca4j-project/discussions) (GitHub Discussions, shared with [FCA4J UI](https://github.com/guti34/fca4j-ui)). Confirmed bugs can be filed as [issues](https://github.com/guti34/fca4j-project/issues).

## Citing FCA4J

If you use FCA4J in academic work, please cite:

> A. Gutierrez, M. Huchard, P. Martin. "FCA4J: A Java Library for Relational Concept Analysis and Formal Concept Analysis." In *Proceedings of the 16th International Conference on Concept Lattices and Their Applications (CLA 2022)*, P. Cordero and O. Kridlo (eds.), CEUR-WS Vol-3308, pp. 207–212, 2022.

```bibtex
@inproceedings{gutierrez2022fca4j,
  author    = {Gutierrez, Alain and Huchard, Marianne and Martin, Pierre},
  title     = {{FCA4J}: A {Java} Library for Relational Concept Analysis and Formal Concept Analysis},
  booktitle = {Proceedings of the 16th International Conference on Concept Lattices and Their Applications (CLA 2022)},
  editor    = {Cordero, Pablo and Kridlo, Ondrej},
  series    = {CEUR Workshop Proceedings},
  volume    = {3308},
  pages     = {207--212},
  year      = {2022},
  url       = {https://ceur-ws.org/Vol-3308/Paper17.pdf}
}
```

## License

BSD 3-Clause License — see [LICENCE.txt](LICENCE.txt). Copyright © 2022 LIRMM.

## Team

Developed by the [Marel team](https://www.lirmm.fr/equipes/marel/), LIRMM (CNRS / Université de Montpellier):

- Marianne Huchard — LIRMM
- Pierre Martin — CIRAD
- Alain Gutierrez — LIRMM (technical maintainer)
