param([Parameter(Mandatory=$true)][string]$RuntimeRoot,[string]$ProjectRoot='C:\Users\Juliana Trabalho\Documents\ECP\kit-inicio')
$ErrorActionPreference='Stop'
$config=Get-Content -Raw -LiteralPath (Join-Path $RuntimeRoot 'native-config.json') | ConvertFrom-Json
$pg=Join-Path $RuntimeRoot 'postgres\pgsql\bin\pg_ctl.exe'
$data=Join-Path $RuntimeRoot 'pgdata'
$status=Start-Process -FilePath $pg -ArgumentList @('-D',('"'+$data+'"'),'status') -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput (Join-Path $RuntimeRoot 'postgres-status.log') -RedirectStandardError (Join-Path $RuntimeRoot 'postgres-status-error.log')
if($status.ExitCode-ne 0){
 $started=Start-Process -FilePath $pg -ArgumentList @('-D',('"'+$data+'"'),'-l',('"'+(Join-Path $RuntimeRoot 'postgres.log')+'"'),'-w','start') -WindowStyle Hidden -Wait -PassThru
 if($started.ExitCode-ne 0){throw 'O banco local não iniciou. Veja postgres.log.'}
}
function Has-Listener([int]$Port){$socket=[Net.Sockets.TcpClient]::new();try{$socket.Connect('127.0.0.1',$Port);return $true}catch{return $false}finally{$socket.Dispose()}}
if(-not(Has-Listener 8080)){
 $env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55432/radar_local';$env:SPRING_DATASOURCE_USERNAME='app_aplicacao';$env:SPRING_DATASOURCE_PASSWORD=$config.App
 $env:APP_DOCUMENTO_HMAC_CHAVE=$config.Hmac;$env:APP_CORS_ORIGENS='http://127.0.0.1:3000,http://localhost:3000';$env:SERVER_ADDRESS='127.0.0.1';$env:SERVER_PORT='8080'
 $env:TEMP='C:\Users\Juliana Trabalho\Documents\Codex\radar-tmp';$env:TMP=$env:TEMP
 $jar=Join-Path $ProjectRoot 'backend\target\plataforma-backend.jar'
 Start-Process -FilePath 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot\bin\java.exe' -ArgumentList @('-jar',('"'+$jar+'"')) -WorkingDirectory $ProjectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $RuntimeRoot 'backend.log') -RedirectStandardError (Join-Path $RuntimeRoot 'backend-error.log') | Out-Null
 $env:SPRING_DATASOURCE_PASSWORD=$null;$env:APP_DOCUMENTO_HMAC_CHAVE=$null
}
if(-not(Has-Listener 3000)){
 $env:BACKEND_URL='http://127.0.0.1:8080';$env:NEXT_TELEMETRY_DISABLED='1'
 $front=Join-Path $ProjectRoot 'frontend';$next=Join-Path $front 'node_modules\next\dist\bin\next'
 Start-Process -FilePath 'C:\Program Files\nodejs\node.exe' -ArgumentList @(('"'+$next+'"'),'dev','--hostname','127.0.0.1','--port','3000') -WorkingDirectory $front -WindowStyle Hidden -RedirectStandardOutput (Join-Path $RuntimeRoot 'frontend.log') -RedirectStandardError (Join-Path $RuntimeRoot 'frontend-error.log') | Out-Null
}
$ready=$false
for($attempt=0;$attempt-lt 45;$attempt++){
 try{$health=Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2;$page=Invoke-WebRequest 'http://127.0.0.1:3000/radar' -TimeoutSec 3 -UseBasicParsing;if($health.status-eq 'UP' -and $page.Content.Contains('Radar')){$ready=$true;break}}catch{}
 Start-Sleep -Seconds 1
}
if(-not $ready){throw 'Não foi possível confirmar os serviços. Consulte os logs locais; nenhuma outra aplicação foi encerrada.'}
Write-Output 'Radar pronto em http://127.0.0.1:3000/radar'
Write-Output 'Guia: http://127.0.0.1:3000/radar/guia'
Write-Output 'Este ambiente usa PostgreSQL nativo. Não usa Docker.'
