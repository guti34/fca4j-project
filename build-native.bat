@echo off
REM ===================================================================
REM  build-native.bat
REM  Compile la lib native fca4j (tous les algos, dont Pluton) sous
REM  Windows avec MinGW-w64 gcc, la deploie dans les resources du
REM  module, et reconstruit le jar applicatif.
REM
REM  Prerequis (dans le PATH) : mvn, cmake, gcc/mingw32-make (MinGW-w64).
REM  Verifie une fois : gcc --version, cmake --version, mvn -v
REM ===================================================================
setlocal

REM --- Chemins (a adapter si l'arborescence change) -------------------
set MODULE_ROOT=C:\platform\fca4j-project\fca4j-core-natif
set NATIVE_DIR=%MODULE_ROOT%\src\main\native
set JNI_HEADER=%NATIVE_DIR%\fr_lirmm_fca4j_core_natif_NativeBridge.h
set LIB_NAME=fca4j_dbasis.dll
REM Emplacement d'ou NativeBridge charge la lib (vu dans les logs :
REM   "/native/windows-x86_64/fca4j_dbasis.dll")
set LIB_DEST=%MODULE_ROOT%\src\main\resources\native\windows-x86_64

echo ============================================================
echo  [1/6] Regeneration de l'en-tete JNI (mvn compile)
echo ============================================================
cd /d "%MODULE_ROOT%"
call mvn -q compile
if errorlevel 1 goto :fail

echo ============================================================
echo  [2/6] Verification des prototypes Pluton dans le .h
echo ============================================================
findstr /C:"runPluton" "%JNI_HEADER%" >nul
if errorlevel 1 (
    echo   ECHEC : runPluton absent de l'en-tete JNI genere.
    echo   -^> Verifier les declarations 'native' dans NativeBridge.java,
    echo      puis que le POM passe bien -h a javac.
    goto :fail
)
echo   OK : prototypes Pluton presents.

echo ============================================================
echo  [3/6] Nettoyage du dossier build (evite les caches CMake)
echo ============================================================
if exist "%NATIVE_DIR%\build" rmdir /s /q "%NATIVE_DIR%\build"

echo ============================================================
echo  [4/6] Configuration CMake (MinGW Makefiles, Release)
echo ============================================================
cd /d "%NATIVE_DIR%"
cmake -S . -B build -G "MinGW Makefiles" -DCMAKE_BUILD_TYPE=Release
if errorlevel 1 goto :fail

echo ============================================================
echo  [5/6] Compilation
echo ============================================================
cmake --build build --config Release
if errorlevel 1 goto :fail

echo ============================================================
echo  [6/6] Deploiement de la lib + rebuild du jar
echo ============================================================
if not exist "%LIB_DEST%" mkdir "%LIB_DEST%"
copy /Y "%NATIVE_DIR%\build\%LIB_NAME%" "%LIB_DEST%\%LIB_NAME%"
if errorlevel 1 (
    echo   ECHEC : copie de %LIB_NAME% vers %LIB_DEST%
    echo   -^> Verifier que le build a bien produit build\%LIB_NAME%
    echo      et qu'aucun process Java ne verrouille la DLL cible.
    goto :fail
)
echo   Lib deployee : %LIB_DEST%\%LIB_NAME%

REM Reconstruit le jar pour qu'il embarque la nouvelle DLL.
REM (Le module app-light est celui qui produit le jar-with-dependencies
REM  utilise en ligne de commande ; adapter le -pl si besoin.)
cd /d "C:\platform\fca4j-project"
call mvn -q -DskipTests package
if errorlevel 1 goto :fail

echo.
echo ============================================================
echo  BUILD OK
echo ============================================================
goto :end

:fail
echo.
echo ============================================================
echo  BUILD ECHOUE (voir le message ci-dessus)
echo ============================================================
exit /b 1

:end
endlocal
