# Exercises the deployment/rollback control flow using a mock Docker command; no real containers are changed.
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$global:deployTestCalls = [System.Collections.Generic.List[object]]::new()
$global:deployTestFailNextUp = $false
function global:docker {
    $arguments = @($args)
    $global:deployTestCalls.Add(@{ arguments = $arguments; backend = $env:BACKEND_IMAGE; frontend = $env:FRONTEND_IMAGE })
    $global:LASTEXITCODE = 0
    if ($arguments[0] -eq 'inspect') {
        if ($arguments[-1] -eq 'smart-shift-backend') { 'smart-shift/backend:previous' }
        else { 'smart-shift/frontend:previous' }
    } elseif ($arguments[0] -eq 'image') { 'sha256:local-test-only' }
    elseif ($arguments -contains 'up' -and $global:deployTestFailNextUp) {
        $global:deployTestFailNextUp = $false
        $global:LASTEXITCODE = 1
    }
}
function Assert-Deployment([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
    Write-Output "PASS $Message"
}
try {
    $oldBackend = $env:BACKEND_IMAGE
    $oldFrontend = $env:FRONTEND_IMAGE
    & "$taskRoot/ops/deploy.ps1" -Version 'qa-test' -Build
    $builds = @($global:deployTestCalls | Where-Object { $_.arguments -contains 'build' })
    $starts = @($global:deployTestCalls | Where-Object { $_.arguments -contains 'up' })
    Assert-Deployment ($builds.Count -eq 1 -and $builds[0].arguments -contains 'backend' -and $builds[0].arguments -contains 'frontend') 'both application images are built before deployment'
    Assert-Deployment ($starts.Count -eq 1 -and $starts[0].arguments -contains '--wait' -and $starts[0].arguments -contains '--no-build') 'deployment waits for health without rebuilding'
    Assert-Deployment ($env:BACKEND_IMAGE -eq $oldBackend -and $env:FRONTEND_IMAGE -eq $oldFrontend) 'caller image environment is restored'
    $global:deployTestCalls.Clear()
    $global:deployTestFailNextUp = $true
    $failed = $false
    try { & "$taskRoot/ops/deploy.ps1" -Version 'qa-failure' } catch { $failed = $true }
    $starts = @($global:deployTestCalls | Where-Object { $_.arguments -contains 'up' })
    Assert-Deployment ($failed -and $starts.Count -eq 2) 'failed readiness reports failure after rollback'
    Assert-Deployment ($starts[1].backend -eq 'smart-shift/backend:previous' -and $starts[1].frontend -eq 'smart-shift/frontend:previous') 'rollback restores both previous images'
    Assert-Deployment ($env:BACKEND_IMAGE -eq $oldBackend -and $env:FRONTEND_IMAGE -eq $oldFrontend) 'failure also restores caller environment'
} finally {
    Remove-Item Function:/docker
    Remove-Variable deployTestCalls, deployTestFailNextUp -Scope Global
}
