param(
    [switch]$SkipPackage,
    [switch]$SkipBuild,
    [switch]$TwitchEnv,
    [string]$EnvFile = ".env.twitch.local",
    [string[]]$Channels = @(),
    [switch]$ForceRecreate
)

$ErrorActionPreference = "Stop"

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
    # Same rules as `make secrets`: a missing file whose Compose volume already exists means the volume
    # holds an older credential, so stop and say so; every other missing file gets a fresh random value.
    $project = if ($env:COMPOSE_PROJECT_NAME) { $env:COMPOSE_PROJECT_NAME } else { (Split-Path -Leaf (Get-Location)).ToLowerInvariant() }
    foreach ($pair in @(@("POSTGRES_PASSWORD", "postgres-data"), @("STREAMSENSE_FRAME_STORAGE_ACCESS_KEY", "minio-data"), @("STREAMSENSE_FRAME_STORAGE_SECRET_KEY", "minio-data"))) {
        $name, $volume = $pair
        if (-not (Test-Path -LiteralPath "secrets/$name")) {
            $existing = docker volume ls -q --filter "label=com.docker.compose.project=$project" --filter "label=com.docker.compose.volume=$volume" 2>$null
            if ($existing) {
                throw "secrets/$name is missing, but the Compose volume '$volume' already exists and was initialised with an older credential. Write that credential into secrets/$name to keep the data, or run 'make nuke' to discard the volume; then rerun."
            }
        }
    }
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    Get-ChildItem -Path "secrets" -Filter "*.example" | ForEach-Object {
        $name = $_.Name -replace "\.example$", ""
        $target = Join-Path $_.DirectoryName $name
        if (-not (Test-Path -LiteralPath $target)) {
            $byteCount = switch ($name) {
                "STREAMSENSE_FRAME_STORAGE_ACCESS_KEY" { 8 }
                "STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET" { 32 }
                default { 16 }
            }
            $buffer = New-Object byte[] $byteCount
            $rng.GetBytes($buffer)
            $value = ($buffer | ForEach-Object { $_.ToString("x2") }) -join ""
            [System.IO.File]::WriteAllText($target, "$value`n")
            "created $target with a random value"
        }
    }
    $rng.Dispose()
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
    Run-Step "Package Java services (one reactor build)" {
        # In a git worktree `.git` is a file that points outside the bind mount, so the git-commit-id
        # plugin cannot open the repository inside the container; skip it there (build-info still works).
        $mavenArgs = @("-B", "-ntp", "-DskipTests", "package")
        if (Test-Path -LiteralPath ".git" -PathType Leaf) {
            "git worktree detected: skipping git-commit-id metadata for the container build"
            $mavenArgs = @("-Dmaven.gitcommitid.skip=true") + $mavenArgs
        }
        docker run --rm -v "${PWD}:/workspace" -w "/workspace" maven:3.9.9-eclipse-temurin-21 mvn @mavenArgs
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
