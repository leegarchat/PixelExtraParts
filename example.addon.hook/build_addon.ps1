param (
    [Parameter(Position=0)]
    [string]$AddonName,
    
    [Parameter(Position=1)]
    [string]$ProjectPath = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot

# Force Java tools to use UTF-8 globally to prevent encoding issues on Windows
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"

function Show-Help {
    Write-Host "Builds a Pine addon module into a DEX JAR for Windows."
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  .\build_addon.ps1 <ADDON_NAME> [PROJECT_PATH]"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\build_addon.ps1 test_project"
    Write-Host "  .\build_addon.ps1 my_hook .\my_hook"
    exit 0
}

if ([string]::IsNullOrWhiteSpace($AddonName) -or $AddonName -eq "-h" -or $AddonName -eq "--help") {
    Show-Help
}

if ($AddonName -match "\s") {
    Write-Error "Addon name must not contain spaces: '$AddonName'"
    exit 1
}

# Project path resolution
if ([string]::IsNullOrWhiteSpace($ProjectPath)) {
    if (Test-Path (Join-Path $ScriptDir $AddonName)) {
        $ProjectRoot = Join-Path $ScriptDir $AddonName
    } else {
        $ProjectRoot = $ScriptDir
    }
} else {
    $ProjectRoot = Resolve-Path $ProjectPath | Select-Object -ExpandProperty Path
}

$SrcDir   = Join-Path $ProjectRoot "src"
$MetaDir  = Join-Path $ProjectRoot "META-INF"
$OutDir   = Join-Path $ProjectRoot "out"
$BuildDir = Join-Path $ProjectRoot "build"

# Dependency paths
$PineXposedJar = Join-Path $ScriptDir "prebuild\pine\pine-xposed.jar"
$PineCoreJar   = Join-Path $ScriptDir "prebuild\pine\pine-core.jar"
$XposedApiJar  = Join-Path $ScriptDir "prebuild\xposed\api-82.jar"
$CoreSrc       = Join-Path $ScriptDir "prebuild\IAddonHook.java"

# Find d8.jar
$D8Jar = $env:D8_JAR
if ([string]::IsNullOrWhiteSpace($D8Jar) -or -not (Test-Path $D8Jar)) {
    $D8Jar = Join-Path $ScriptDir "prebuild\sdk\d8.jar"
    if (-not (Test-Path $D8Jar)) {
        $SdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
        if ($SdkRoot) {
            $D8Candidates = Get-ChildItem -Path "$SdkRoot\build-tools" -Filter "d8.jar" -Recurse -ErrorAction SilentlyContinue | Sort-Object FullName -Descending
            if ($D8Candidates) { $D8Jar = $D8Candidates[0].FullName }
        }
    }
}

# Find android.jar
$AndroidJar = $env:ANDROID_JAR
if ([string]::IsNullOrWhiteSpace($AndroidJar) -or -not (Test-Path $AndroidJar)) {
    $AndroidJar = Join-Path $ScriptDir "prebuild\android.jar"
    if (-not (Test-Path $AndroidJar)) {
        $SdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
        if ($SdkRoot) {
            $AndroidCandidates = Get-ChildItem -Path "$SdkRoot\platforms" -Filter "android.jar" -Recurse -ErrorAction SilentlyContinue | Sort-Object FullName -Descending
            if ($AndroidCandidates) { $AndroidJar = $AndroidCandidates[0].FullName }
        }
    }
}

Write-Host "=== Building addon: $AddonName ===" -ForegroundColor Cyan

# Validations
if (-not (Get-Command "javac" -ErrorAction SilentlyContinue)) { Write-Error "Java (javac) not found."; exit 1 }
if (-not (Test-Path $SrcDir)) { Write-Error "Directory not found: $SrcDir"; exit 1 }
if (-not (Test-Path "$MetaDir\addon.json")) { Write-Error "Manifest not found: $MetaDir\addon.json"; exit 1 }
if (-not (Test-Path $AndroidJar)) { Write-Error "android.jar not found!"; exit 1 }
if (-not (Test-Path $D8Jar)) { Write-Error "d8.jar not found!"; exit 1 }

Write-Host "  Using android.jar: $AndroidJar"
Write-Host "  Using d8: $D8Jar"

# Clean and create directories
if (Test-Path $BuildDir) { Remove-Item -Path $BuildDir -Recurse -Force }
if (Test-Path $OutDir) { Remove-Item -Path $OutDir -Recurse -Force }
New-Item -ItemType Directory -Path "$BuildDir\stubs" | Out-Null
New-Item -ItemType Directory -Path "$BuildDir\classes" | Out-Null
New-Item -ItemType Directory -Path "$BuildDir\dex" | Out-Null
New-Item -ItemType Directory -Path $OutDir | Out-Null

Write-Host "  [1/4] Compiling IAddonHook..."
& javac --release 11 -encoding UTF-8 -parameters -classpath $AndroidJar -d "$BuildDir\stubs" $CoreSrc
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "  [2/4] Compiling addon sources..."
# В Windows разделитель classpath — это точка с запятой (;)
$AddonCp = "$AndroidJar;$BuildDir\stubs"
if (Test-Path $PineXposedJar) { $AddonCp += ";$PineXposedJar" }
if (Test-Path $PineCoreJar) { $AddonCp += ";$PineCoreJar" }
if (Test-Path $XposedApiJar) { $AddonCp += ";$XposedApiJar" }

# Get list of Java files
$SrcFiles = Get-ChildItem -Path $SrcDir -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName

if ($SrcFiles) {
    # Explicitly export the list of files in UTF-8 WITHOUT BOM using .NET
    # This prevents the `?\...` error in javac caused by the invisible BOM character
    $Utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllLines("$BuildDir\sources.txt", $SrcFiles, $Utf8NoBom)

    & javac --release 11 -encoding UTF-8 -parameters -classpath $AddonCp -d "$BuildDir\classes" "@$BuildDir\sources.txt"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "  [3/4] Converting to DEX..."
$D8Args = @("--lib", $AndroidJar)
if (Test-Path $PineXposedJar) { $D8Args += "--classpath"; $D8Args += $PineXposedJar }
if (Test-Path $PineCoreJar) { $D8Args += "--classpath"; $D8Args += $PineCoreJar }
if (Test-Path $XposedApiJar) { $D8Args += "--classpath"; $D8Args += $XposedApiJar }

$ClassesJar = "$BuildDir\classes_tmp.jar"
Push-Location "$BuildDir\classes"
& jar cf $ClassesJar .
Pop-Location

$D8Args += "--output"
$D8Args += "$BuildDir\dex"
$D8Args += "--min-api"
$D8Args += "26"
$D8Args += $ClassesJar

& java -cp $D8Jar com.android.tools.r8.D8 $D8Args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "  [4/4] Packaging JAR..."
Push-Location "$BuildDir\dex"
New-Item -ItemType Directory -Path "META-INF" | Out-Null
Copy-Item "$MetaDir\addon.json" -Destination "META-INF\"
& jar cf "$OutDir\$AddonName.jar" classes.dex META-INF\
Pop-Location

Write-Host "  Cleaning up build directory..."
Remove-Item -Path $BuildDir -Recurse -Force

Write-Host "`n=== Done ===" -ForegroundColor Green
Write-Host "  Result: $OutDir\$AddonName.jar`n"