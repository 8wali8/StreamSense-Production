param(
    [switch]$SkipPackage,
    [switch]$SkipBuild,
    [switch]$TwitchEnv,
    [string]$EnvFile = ".env.twitch.local",
    [string[]]$Channels = @(),
    [switch]$ForceRecreate
)

$ErrorActionPreference = "Stop"

$javaServices = @(
    "eureka-server",
    "config-server",
    "api-gateway",
    "chat-service",
    "sentiment-service",
    "video-service",
    "recommendation-service",
    "analytics-service"
)

function Run-Step {
    param(
        [string]$Name,
        [scriptblock]$Command
    )

    Write-Host "`n===== $Name ====="
    & $Command
}

function Response-Text {
    param($Response)

    if ($Response.Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Response.Content)
    }

    return $Response.Content
}

function Load-EnvFile {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Env file not found: $Path"
    }

    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line -match "^([^=]+)=(.*)$") {
            $name = $Matches[1].Trim()
            $value = $Matches[2].Trim().Trim('"').Trim("'")
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

function Csv-Channels {
    param([string]$Value)

    if (-not $Value) {
        return @()
    }

    return @($Value.Split(",") | ForEach-Object { $_.Trim().TrimStart("@") } | Where-Object { $_ })
}

Run-Step "Check Docker" {
    docker --version
    docker compose version
}

Run-Step "Ensure local secrets" {
    Get-ChildItem -Path "secrets" -Filter "*.example" | ForEach-Object {
        $target = Join-Path $_.DirectoryName ($_.Name -replace "\.example$", "")
        if (-not (Test-Path -LiteralPath $target)) {
            Copy-Item -LiteralPath $_.FullName -Destination $target
            "created $target from example"
        }
    }
    "secrets present under ./secrets"
}

if ($TwitchEnv) {
    Run-Step "Load Twitch env" {
        Load-EnvFile $EnvFile
        "loaded $EnvFile"
    }
}

if ($Channels.Count -eq 0) {
    $Channels = @(Csv-Channels $env:TWITCH_CHANNELS)
}

if ($Channels.Count -eq 0) {
    $Channels = @(Csv-Channels $env:TWITCH_VIDEO_CHANNELS)
}

if (-not $SkipPackage) {
    foreach ($service in $javaServices) {
        Run-Step "Package $service" {
            docker run --rm -v "${PWD}:/workspace" -w "/workspace/$service" maven:3.9.9-eclipse-temurin-21 mvn -B -ntp -DskipTests package
        }
    }
}

if ($SkipBuild) {
    Run-Step "Start Compose stack" {
        if ($ForceRecreate) {
            docker compose up -d --force-recreate
        } else {
            docker compose up -d
        }
    }
} else {
    Run-Step "Build and start Compose stack" {
        if ($ForceRecreate) {
            docker compose up -d --build --force-recreate
        } else {
            docker compose up -d --build
        }
    }
}

# Docker Desktop can occasionally report a transient dependency/API error while
# recreating containers. A second non-build start usually lets healthy
# dependencies continue and starts the remaining services.
Run-Step "Ensure remaining services are started" {
    docker compose up -d
}

if ($TwitchEnv) {
    Run-Step "Recreate Twitch ingest services with env" {
        docker compose up -d --force-recreate chat-service video-capture-service
    }

    Run-Step "Ensure gateway is ready after Twitch service restart" {
        docker compose up -d --no-deps --force-recreate api-gateway frontend
    }
}

if ($Channels.Count -gt 0) {
    Run-Step "Switch runtime channels" {
        $body = @{ channels = @($Channels) } | ConvertTo-Json -Depth 4
        $headers = @{ "Content-Type" = "application/json" }

        $chat = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://localhost:8080/api/chat/twitch/channels" -Headers $headers -Body $body -TimeoutSec 30
        "chat runtime update: $(Response-Text $chat)"

        $video = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://localhost:8080/api/video/capture/channels" -Headers $headers -Body $body -TimeoutSec 30
        "video runtime update: $(Response-Text $video)"
    }
}

Run-Step "Compose status" {
    docker compose ps
}

Run-Step "Smoke check frontend" {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:3000/" -TimeoutSec 20
    "frontend status: $($response.StatusCode)"
}

Run-Step "Smoke check api-gateway" {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/actuator/health" -TimeoutSec 20
    "api-gateway health: $(Response-Text $response)"
}

Run-Step "Smoke check ml-engine" {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8000/ml/health" -TimeoutSec 20
    "ml-engine health: $(Response-Text $response)"
}

Run-Step "Twitch status" {
    $chat = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/api/chat/twitch/status" -TimeoutSec 20
    "chat status: $(Response-Text $chat)"

    $video = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/api/video/capture/status" -TimeoutSec 20
    "video status: $(Response-Text $video)"
}

Write-Host "`nStack is up."
Write-Host "frontend:    http://localhost:3000"
Write-Host "api-gateway: http://localhost:8080"
Write-Host "grafana:     http://localhost:3001"
Write-Host "kafka-ui:    http://localhost:8088"
Write-Host "minio:       http://localhost:9001"
