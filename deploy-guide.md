# Guide de déploiement FCA4J

## fca4j-project

### 1. Vérifications locales

```bash
mvn clean install -DskipTests   # build complet + install local
mvn test                         # validation tests
```

Si du code natif a changé, recompiler la DLL Windows et la committer :

```bat
cd fca4j-core-natif\src\main\native
cmake -B build -G "MinGW Makefiles" -DCMAKE_BUILD_TYPE=Release
cmake --build build
copy build\fca4j_dbasis.dll ..\resources\native\windows-x86_64\
```

### 2. Bump de version (si nouvelle version)

```bash
mvn versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
# vérifier, puis :
git add -A && git commit -m "chore: bump version to X.Y.Z"
```

### 3. Déploiement sur Maven Central

```bash
mvn clean deploy -P release      # signe + pousse sur OSSRH
```

> Nécessite le profil `release` dans `settings.xml` avec credentials OSSRH et clé GPG.  
> Aller valider la release sur [https://oss.sonatype.org](https://oss.sonatype.org) (Close → Release).

### 4. Push GitLab → fat JAR CI

```bash
git push origin main             # déclenche le miroir GitLab → GitHub
```

Le pipeline GitHub Actions se lance automatiquement :

- compile `libfca4j_dbasis.so` (Linux) et `libfca4j_dbasis.dylib` (macOS ARM + Intel)
- assemble le fat JAR multiplateforme
- publie sur la release **`continuous`** de `fca4j-project`
- déclenche le build `fca4j-ui`

**URL stable du fat JAR :**  
`https://github.com/guti34/fca4j-project/releases/download/continuous/fca4j.jar`

### 5. Release versionnée (optionnel)

```bash
git tag vX.Y.Z && git push origin vX.Y.Z
```

---

## fca4j-ui

### Déclenchement automatique

Le build des installeurs se lance automatiquement après chaque fat JAR publié
(via `repository_dispatch`). Aucune action manuelle requise.

**URLs des installeurs (build continu) :**  
`https://github.com/guti34/fca4j-ui/releases/tag/continuous-ui`

### Release versionnée

Pour publier des installeurs officiels attachés à un numéro de version :

```bash
# Depuis le dépôt fca4j-ui (GitLab ou GitHub)
git tag vX.Y.Z && git push origin vX.Y.Z
```

Le pipeline produit MSI (Windows), DEB (Linux), DMG (macOS) et crée
une GitHub Release versionnée.

### Déclenchement manuel

Si besoin de relancer sans push :  
GitHub → dépôt `fca4j-ui` → **Actions → Build & Release → Run workflow**

---

## Récapitulatif des URLs permanentes

| Ressource              | URL                                                                                      |
|------------------------|------------------------------------------------------------------------------------------|
| fat JAR (latest)       | `https://github.com/guti34/fca4j-project/releases/download/continuous/fca4j.jar`        |
| Installeurs (latest)   | `https://github.com/guti34/fca4j-ui/releases/tag/continuous-ui`                         |
| Maven Central          | `https://central.sonatype.com/artifact/fr.lirmm.fca4j/fca4j-parent`                     |
| GitLab source          | `https://gite.lirmm.fr/gutierre/fca4j-project`                                          |
| GitHub mirror          | `https://github.com/guti34/fca4j-project`                                               |
