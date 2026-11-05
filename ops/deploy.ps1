param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$')]
    [string]$Version,
    [switch]$Build,
    [switch]$Monitoring
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $taskRoot
$oldBackend = $env:BACKEND_IMAGE
$oldFrontend = $env:FRONTEND_IMAGE
$composeArgs = @('compose', '-f', 'docker-compose.yml', '-f', 'compose.release.yml')
if ($Monitoring) { $composeArgs += @('-f', 'compose.monitoring.yml') }
function Invoke-Compose([string[]]$Operation) {
    & docker @composeArgs @Operation
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed: $($Operation -join ' ')" }
}
function Get-InstalledImage([string]$Container) {
    # Missing containers are expected on the first deployment.
    $nativePreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $value = & docker inspect --format '{{.Config.Image}}' $Container 2>$null
        if ($LASTEXITCODE -eq 0) { return [string]$value }
        return $null
    } finally { $ErrorActionPreference = $nativePreference }
}
try {
    if ($Monitoring) {
        & python qa-automation/scripts/setup_monitoring.py
        if ($LASTEXITCODE -ne 0) { throw 'Monitoring secrets are not ready' }
    }
    $env:BACKEND_IMAGE = "smart-shift/backend:$Version"
    $env:FRONTEND_IMAGE = "smart-shift/frontend:$Version"
    Invoke-Compose -Operation @('config', '--quiet')
    if ($Build) { Invoke-Compose -Operation @('build', 'backend', 'frontend') }
    foreach ($image in @($env:BACKEND_IMAGE, $env:FRONTEND_IMAGE)) {
        & docker image inspect $image --format '{{.Id}}' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Missing local image $image; build first with -Build" }
    }
    $previousBackend = Get-InstalledImage 'smart-shift-backend'
    $previousFrontend = Get-InstalledImage 'smart-shift-frontend'
    try {
        Invoke-Compose -Operation @('up', '-d', '--no-build', '--wait', '--wait-timeout', '180')
        Write-Output "Deployment $Version is ready. Database volumes were preserved."
        if ($previousBackend -and $previousFrontend) {
            Write-Output "Previous backend image: $previousBackend"
            Write-Output "Previous frontend image: $previousFrontend"
        }
    } catch {
        if ($previousBackend -and $previousFrontend) {
            Write-Warning 'Readiness failed. Restoring previous application images.'
            $env:BACKEND_IMAGE = $previousBackend
            $env:FRONTEND_IMAGE = $previousFrontend
            Invoke-Compose -Operation @('up', '-d', '--no-build', '--wait', '--wait-timeout', '180')
        }
        throw
    }
} finally {
    $env:BACKEND_IMAGE = $oldBackend
    $env:FRONTEND_IMAGE = $oldFrontend
    Pop-Location
}
