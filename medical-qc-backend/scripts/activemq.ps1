param(
    [ValidateSet('start', 'stop', 'status', 'console')]
    [string]$Action = 'status',
    [string]$ActiveMqHome = 'D:\activemq\apache-activemq-5.16.6-bin\apache-activemq-5.16.6'
)

$activemqBat = Join-Path $ActiveMqHome 'bin\activemq.bat'

if (-not (Test-Path $activemqBat)) {
    throw "未找到 ActiveMQ 启动脚本: $activemqBat"
}

switch ($Action) {
    'start' { & $activemqBat start }
    'stop' { & $activemqBat stop }
    'status' { & $activemqBat list }
    'console' { & $activemqBat console }
}
