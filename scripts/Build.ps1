param([switch]$Test,[switch]$Release,[string]$JavaHome,[string]$CacheDirectory)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
if(-not $JavaHome){$JavaHome=$env:JAVA_HOME}
if(-not $JavaHome){$JavaHome='C:\Program Files\Android\Android Studio\jbr'}
if(-not(Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))){throw 'Set JAVA_HOME or use -JavaHome with JDK 17+'}
if(-not $CacheDirectory){$CacheDirectory=Join-Path $root 'local\gradle-ascii'}
if($CacheDirectory -match '[^\x00-\x7F]'){throw 'Use -CacheDirectory with an ASCII path (Windows Java socket/worker workaround)'}
$temporary=Join-Path $CacheDirectory 'tmp'
New-Item -ItemType Directory -Path $temporary -Force | Out-Null
$oldJava=$env:JAVA_HOME;$oldCache=$env:GRADLE_USER_HOME;$oldOptions=$env:JAVA_TOOL_OPTIONS
try{
    $env:JAVA_HOME=$JavaHome;$env:GRADLE_USER_HOME=$CacheDirectory
    $env:JAVA_TOOL_OPTIONS=($oldOptions+' -Djava.io.tmpdir="'+$temporary+'" -Djdk.net.unixdomain.tmpdir="'+$temporary+'"').Trim()
    $tasks=@(':app:assembleDebug');if($Release){$tasks=@(':app:assembleRelease')}
    if($Test){$tasks+=@(':app:testDebugUnitTest',':app:lintDebug')}
    Push-Location $root
    try{& .\gradlew.bat @tasks --console=plain;if($LASTEXITCODE){throw 'Android build failed'}}finally{Pop-Location}
    $kind=if($Release){'release'}else{'debug'}
    $file=Join-Path $root ('app\build\outputs\apk\'+$kind+'\app-'+$kind+'.apk')
    if(Test-Path -LiteralPath $file){$out=Join-Path $root 'artifacts';New-Item -ItemType Directory -Path $out -Force | Out-Null;Copy-Item -LiteralPath $file -Destination (Join-Path $out ('AndroidDynamicIsland-0.1.0-'+$kind+'.apk')) -Force;Write-Output ('APK: '+$file)}
}finally{$env:JAVA_HOME=$oldJava;$env:GRADLE_USER_HOME=$oldCache;$env:JAVA_TOOL_OPTIONS=$oldOptions}
