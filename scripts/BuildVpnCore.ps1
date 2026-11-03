param([string]$AndroidHome=$env:ANDROID_HOME,[string]$NdkHome=$env:ANDROID_NDK_HOME)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$work=Join-Path $root 'local'
$source=Join-Path $work 'sing-box'
if(-not $AndroidHome){$AndroidHome='C:\Android'}
if(-not $NdkHome){$NdkHome=Join-Path $work 'android-ndk-r28'}
if(-not(Test-Path -LiteralPath (Join-Path $NdkHome 'source.properties'))){throw 'Install Android NDK 28.0.13004108 and set ANDROID_NDK_HOME.'}
New-Item -ItemType Directory -Path $work -Force|Out-Null
if(-not(Test-Path -LiteralPath (Join-Path $source '.git'))){git clone --depth 1 --branch v1.12.25 https://github.com/SagerNet/sing-box.git $source;if($LASTEXITCODE){throw 'Cannot fetch VPN core'}}
$commit=git -C $source rev-parse HEAD
if($commit -ne '73bfb99ebce7923c485435e4faf8571b412065a9'){throw 'Unexpected VPN core source revision'}
$env:GOTOOLCHAIN='go1.25.8'
$env:GOPATH=Join-Path $work 'go'
$env:GOCACHE=Join-Path $work 'go-cache'
$env:ANDROID_HOME=$AndroidHome
$env:ANDROID_NDK_HOME=$NdkHome
$env:JAVA_HOME=if($env:JAVA_HOME){$env:JAVA_HOME}else{'C:\Program Files\Android\Android Studio\jbr'}
$env:PATH=(Join-Path $env:GOPATH 'bin')+[IO.Path]::PathSeparator+$env:PATH
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.8
if($LASTEXITCODE){throw 'Cannot build gomobile'}
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.8
if($LASTEXITCODE){throw 'Cannot build gobind'}
$output=Join-Path $work 'libbox.aar'
Push-Location $source
try{
    gomobile bind -target android/arm64,android/amd64 -androidapi 31 -javapkg io.nekohasekai -libname box -trimpath -buildvcs=false -ldflags '-X github.com/sagernet/sing-box/constant.Version=1.12.25 -s -w -buildid= -checklinkname=0' -tags with_gvisor,with_utls,with_quic,with_wireguard,with_clash_api -o $output ./experimental/libbox
    if($LASTEXITCODE){throw 'VPN core compilation failed'}
}finally{Pop-Location}
Write-Output ('VPN core: '+$output)
