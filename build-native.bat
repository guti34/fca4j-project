@echo off
REM ===================================================================
REM  build-native.bat
REM
REM  Chaine complete apres modification d'un source C :
REM     compilation CMake  ->  copie de la DLL dans les resources
REM     ->  rebuild du jar Maven  ->  verifications
REM
REM  C'est la DERNIERE etape qui est critique : la DLL est embarquee
REM  comme RESSOURCE dans le jar (NativeBridge la charge depuis le
REM  classpath, chemin /native/windows-x86_64/...). Sans "mvn package",
REM  le jar continue de servir l'ancienne DLL et tout semble normal.
REM
REM  Usage :
REM     build-native.bat              build incremental + jar
REM     build-native.bat /clean       reconfigure CMake de zero
REM     build-native.bat /nojar       C seulement, pas de rebuild Maven
REM     build-native.bat /quick       alias de /nojar
REM     build-native.bat /tests       compile et lance les harnais natifs
REM
REM  /clean est OBLIGATOIRE apres tout deplacement ou suppression de
REM  source C : un cache CMake existant continue de referencer l'ancien
REM  chemin et la reconfiguration automatique n'est pas garantie.
REM
REM  Prerequis dans le PATH : cmake, gcc (MinGW-w64), mvn
REM ===================================================================
setlocal enabledelayedexpansion

REM --- Chemins --------------------------------------------------------
set PROJECT_ROOT=C:\platform\fca4j-project
set MODULE_ROOT=%PROJECT_ROOT%\fca4j-core-natif
set NATIVE_DIR=%MODULE_ROOT%\src\main\native
set BUILD_DIR=%MODULE_ROOT%\build
set LIB_NAME=fca4j_dbasis.dll
set LIB_DEST=%MODULE_ROOT%\src\main\resources\native\windows-x86_64
set APP_TARGET=%PROJECT_ROOT%\fca4j-app-light\target
set REF_FILE=%NATIVE_DIR%\latticecbo_ref.txt

REM --- Environnement ---------------------------------------------------
REM FCA4J_PROFILE fait cracher a latticecbo son rapport detaille (phases,
REM equilibrage, volumes) EN PLUS des signatures canoniques. Si la reference
REM a ete produite avec la variable active et la comparaison sans — ou
REM l'inverse — tout se decale d'une quarantaine de lignes et le harnais
REM signale une divergence qui n'existe pas.
REM
REM On la neutralise donc ici : setlocal la rend locale au script, la variable
REM globale de la session n'est pas touchee. La reference et la comparaison
REM sont ainsi toujours produites dans le meme etat.
set FCA4J_PROFILE=
set FCA4J_TLOG=

REM --- Options --------------------------------------------------------
set DO_CLEAN=0
set DO_JAR=1
set DO_TESTS=0
for %%A in (%*) do (
    if /I "%%A"=="/clean"  set DO_CLEAN=1
    if /I "%%A"=="/nojar"  set DO_JAR=0
    if /I "%%A"=="/quick"  set DO_JAR=0
    if /I "%%A"=="/tests"  set DO_TESTS=1
)

echo ============================================================
echo  [1/7] Verification de l'outillage
echo ============================================================
where cmake >nul 2>&1 || (echo   ECHEC : cmake introuvable dans le PATH. & goto :fail)
where gcc   >nul 2>&1 || (echo   ECHEC : gcc introuvable dans le PATH ^(MinGW-w64^). & goto :fail)
if "%DO_JAR%"=="1" (
    where mvn >nul 2>&1 || (echo   ECHEC : mvn introuvable dans le PATH. & goto :fail)
)
tasklist /FI "IMAGENAME eq java.exe" 2>nul | find /I "java.exe" >nul
if not errorlevel 1 (
    echo   ATTENTION : un processus java.exe tourne. S'il tient le jar ou la
    echo               DLL ouverts, la copie ou le package echouera.
)
echo   OK

echo.
echo ============================================================
echo  [2/7] Configuration CMake
echo ============================================================
if "%DO_CLEAN%"=="1" (
    echo   /clean demande : suppression de "%BUILD_DIR%"
    if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
)
if not exist "%BUILD_DIR%\CMakeCache.txt" (
    echo   Configuration ^(MinGW Makefiles, Release^)...
    cmake -S "%NATIVE_DIR%" -B "%BUILD_DIR%" -G "MinGW Makefiles" -DCMAKE_BUILD_TYPE=Release
    if errorlevel 1 goto :fail
) else (
    echo   Cache existant conserve ^(utiliser /clean pour reconfigurer^).
)

echo.
echo ============================================================
echo  [3/7] Compilation de la lib native
echo ============================================================
REM Horodatage AVANT build, pour verifier ensuite que la DLL a bouge.
set DLL_BEFORE=
if exist "%BUILD_DIR%\%LIB_NAME%" (
    for %%F in ("%BUILD_DIR%\%LIB_NAME%") do set DLL_BEFORE=%%~tF
)
cmake --build "%BUILD_DIR%" --parallel
if errorlevel 1 goto :fail
if not exist "%BUILD_DIR%\%LIB_NAME%" (
    echo   ECHEC : "%BUILD_DIR%\%LIB_NAME%" absent apres compilation.
    goto :fail
)
for %%F in ("%BUILD_DIR%\%LIB_NAME%") do set DLL_AFTER=%%~tF
if "!DLL_BEFORE!"=="!DLL_AFTER!" (
    echo   NOTE : la DLL n'a pas change ^(!DLL_AFTER!^).
    echo          Aucun source C modifie depuis la derniere compilation.
) else (
    echo   DLL compilee : !DLL_AFTER!
)

echo.
echo ============================================================
echo  [4/7] Harnais de verification natifs
echo ============================================================
if "%DO_TESTS%"=="0" (
    echo   Ignore ^(ajouter /tests pour les lancer^).
    goto :skiptests
)
REM Cibles EXCLUDE_FROM_ALL : elles ne sont pas construites par le build
REM ordinaire, il faut les demander nommement.
cmake --build "%BUILD_DIR%" --parallel --target dynorder_test ares_test ceres_test aoc_regress latticecbo_regress
if errorlevel 1 goto :fail

echo.
echo   --- socle DynOrder ---
"%BUILD_DIR%\dynorder_test.exe"
if errorlevel 1 (echo   ECHEC : dynorder_test & goto :fail)

echo.
echo   --- Ares contre Hermes ---
"%BUILD_DIR%\ares_test.exe"
if errorlevel 1 (echo   ECHEC : ares_test & goto :fail)

echo.
echo   --- Ceres contre Hermes ---
"%BUILD_DIR%\ceres_test.exe"
if errorlevel 1 (echo   ECHEC : ceres_test & goto :fail)

echo.
echo   --- signature canonique des AOC-posets ---
REM Meme logique que pour le treillis : la reference doit provenir de l'arbre
REM d'AVANT le changement que l'on cherche a valider.
if not exist "%NATIVE_DIR%\aoc_ref.txt" (
    echo   Aucune reference : "%NATIVE_DIR%\aoc_ref.txt"
    echo   ATTENTION - une reference generee MAINTENANT enregistre le
    echo   comportement APRES modification. Elle ne vaut que comme point de
    echo   depart pour les changements A VENIR.
    "%BUILD_DIR%\aoc_regress.exe" > "%NATIVE_DIR%\aoc_ref.txt"
    if errorlevel 1 (echo   ECHEC : generation de la reference AOC & goto :fail)
    echo   Reference ecrite : %NATIVE_DIR%\aoc_ref.txt
) else (
    "%BUILD_DIR%\aoc_regress.exe" "%NATIVE_DIR%\aoc_ref.txt"
    if errorlevel 1 (echo   ECHEC : un AOC-poset a change & goto :fail)
)

echo.
echo   --- signature canonique du treillis ---
if not exist "%REF_FILE%" (
    echo   Aucune reference : "%REF_FILE%"
    echo.
    echo   ATTENTION - une reference generee MAINTENANT ne vaut rien pour
    echo   valider le changement en cours : elle enregistrerait le
    echo   comportement APRES modification. Elle doit provenir de l'arbre
    echo   d'AVANT. La generer ici ne sert que de point de depart pour les
    echo   changements A VENIR.
    echo.
    "%BUILD_DIR%\latticecbo_regress.exe" > "%REF_FILE%"
    if errorlevel 1 (echo   ECHEC : generation de la reference & goto :fail)
    echo   Reference ecrite : %REF_FILE%
) else (
    "%BUILD_DIR%\latticecbo_regress.exe" "%REF_FILE%"
    if errorlevel 1 (echo   ECHEC : le treillis a change & goto :fail)
)
:skiptests

echo.
echo ============================================================
echo  [5/7] Controle des points d'entree JNI exportes
echo ============================================================
REM Purement informatif : en JNI, une fonction Java_* n'a pas besoin de
REM prototype pour etre compilee et exportee. Ce controle liste ce que la
REM DLL expose reellement, ce qui reste le seul fait qui compte.
where objdump >nul 2>&1
if errorlevel 1 (
    echo   objdump introuvable, controle ignore.
) else (
    set JNI_COUNT=0
    for /f %%N in ('objdump -p "%BUILD_DIR%\%LIB_NAME%" ^| find /C "Java_fr_lirmm_fca4j_core_natif_NativeBridge_"') do set JNI_COUNT=%%N
    echo   !JNI_COUNT! symboles JNI NativeBridge exportes.
    if "!JNI_COUNT!"=="0" (
        echo   ECHEC : aucun point d'entree JNI. La DLL ne servira a rien.
        goto :fail
    )
)

echo.
echo ============================================================
echo  [6/7] Deploiement dans les resources du module
echo ============================================================
if not exist "%LIB_DEST%" mkdir "%LIB_DEST%"
copy /Y "%BUILD_DIR%\%LIB_NAME%" "%LIB_DEST%\%LIB_NAME%" >nul
if errorlevel 1 (
    echo   ECHEC : copie vers "%LIB_DEST%"
    echo   -^> Un processus Java verrouille peut-etre la DLL cible.
    goto :fail
)
echo   Deployee : %LIB_DEST%\%LIB_NAME%

if "%DO_JAR%"=="0" (
    echo.
    echo ============================================================
    echo  [7/7] Rebuild du jar IGNORE ^(/nojar^)
    echo ============================================================
    echo   ATTENTION : le jar embarque encore l'ANCIENNE DLL.
    echo   Les tests lances sur le jar-with-dependencies ne verront PAS
    echo   vos modifications. Relancer sans /nojar avant de mesurer.
    goto :done
)

echo.
echo ============================================================
echo  [7/7] Rebuild du jar applicatif ^(mvn package^)
echo ============================================================
cd /d "%PROJECT_ROOT%"
call mvn -q -DskipTests package
if errorlevel 1 goto :fail

set JAR_FOUND=0
for %%F in ("%APP_TARGET%\*-jar-with-dependencies.jar") do (
    set JAR_FOUND=1
    echo   Jar : %%~nxF
    echo   Date: %%~tF
)
if "!JAR_FOUND!"=="0" (
    echo   ECHEC : aucun *-jar-with-dependencies.jar dans "%APP_TARGET%"
    goto :fail
)

:done
echo.
echo ============================================================
echo  BUILD OK
echo ============================================================
echo.
echo  Verification a l'execution : lancer avec FCA4J_PROFILE=1 et
echo  controler la presence de la ligne "bascules :" dans l'en-tete
echo  du profil. Si elle manque, c'est l'ancienne DLL qui est chargee.
echo.
goto :end

:fail
echo.
echo ============================================================
echo  BUILD ECHOUE ^(voir le message ci-dessus^)
echo ============================================================
endlocal
exit /b 1

:end
endlocal
exit /b 0