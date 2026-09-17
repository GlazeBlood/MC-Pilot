# mc.ps1 - helper for the AI Pilot Minecraft bridge (http://127.0.0.1:8765)
# Usage examples:
#   mc.ps1 health
#   mc.ps1 shot .\shots\mc.png
#   mc.ps1 state
#   mc.ps1 look 90 -10        (absolute yaw pitch)   mc.ps1 look 30 0 rel
#   mc.ps1 move forward down  /  mc.ps1 move forward up
#   mc.ps1 key E tap          /  mc.ps1 key ESC tap / key F3 tap
#   mc.ps1 char "hello"
#   mc.ps1 click left         (click|down|up; left|right|middle)
#   mc.ps1 cursor 400 300
#   mc.ps1 clickat 400 300 [left|right|middle]
#   mc.ps1 virtual on|off [guiOnly]
#   mc.ps1 scroll -1
#   mc.ps1 chat "hi"          /  mc.ps1 chat "/time set day"
#   mc.ps1 hotbar 2
#   mc.ps1 gui inventory | gui chat [text] | gui close
#   mc.ps1 focus
#   mc.ps1 pause on|off
param(
    [Parameter(Position = 0)][string]$Cmd = "help",
    [Parameter(Position = 1, ValueFromRemainingArguments = $true)][string[]]$Rest = @(),
    [int]$Port = 8765,
    [string]$Token = "",
    [string]$Base = ""
)
$ErrorActionPreference = "Stop"
if (-not $Base) { $Base = "http://127.0.0.1:$Port" }

function Get-Hdr {
    if ($Token) { return @{ "X-Ai-Pilot-Token" = $Token } }
    return @{}
}

function Get-J($p) {
    Invoke-RestMethod -Method Get -Uri ($Base + $p) -Headers (Get-Hdr)
}

function Post-J($p, $obj) {
    Invoke-RestMethod -Method Post -Uri ($Base + $p) -Headers (Get-Hdr) `
        -ContentType "application/json; charset=utf-8" -Body ($obj | ConvertTo-Json -Compress)
}

try {
    switch ($Cmd.ToLower()) {
        "health" { Get-J "/health" | ConvertTo-Json -Depth 5 }
        "state"  { Get-J "/state" | ConvertTo-Json -Depth 8 }
        "shot" {
            if ($Rest.Count -lt 1) { throw "usage: mc.ps1 shot <out.png>" }
            $out = $Rest[0]
            $dir = Split-Path -Parent $out
            if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
            Invoke-WebRequest -Uri ($Base + "/screenshot") -Headers (Get-Hdr) -OutFile $out | Out-Null
            $f = Get-Item $out
            "saved $($f.FullName) ($([math]::Round($f.Length / 1KB, 1)) KB)"
        }
        "look" {
            $o = @{}
            if ($Rest.Count -ge 1) { $o.yaw = [double]$Rest[0] }
            if ($Rest.Count -ge 2) { $o.pitch = [double]$Rest[1] }
            if ($Rest -contains "rel" -or $Rest -contains "relative") { $o.relative = $true }
            Post-J "/look" $o | ConvertTo-Json -Depth 5
        }
        "move" {
            if ($Rest.Count -lt 2) { throw "usage: mc.ps1 move <forward|back|left|right|jump|sneak|sprint> <down|up>" }
            Post-J "/move" @{ key = $Rest[0]; pressed = ($Rest[1] -eq "down") } | ConvertTo-Json -Depth 5
        }
        "key" {
            if ($Rest.Count -lt 1) { throw "usage: mc.ps1 key <NAME> [tap|down|up]" }
            $act = "tap"
            if ($Rest.Count -ge 2) {
                $act = switch ($Rest[1]) { "down" { "press" } "up" { "release" } default { "tap" } }
            }
            Post-J "/key" @{ key = $Rest[0].ToUpper(); action = $act } | ConvertTo-Json -Depth 5
        }
        "char" {
            if ($Rest.Count -lt 1) { throw 'usage: mc.ps1 char "<text>"' }
            Post-J "/char" @{ text = ($Rest -join " ") } | ConvertTo-Json -Depth 5
        }
        "click" {
            $btn = "left"; $act = "click"
            if ($Rest.Count -ge 1) { $btn = $Rest[0] }
            if ($Rest.Count -ge 2) { $act = switch ($Rest[1]) { "down" { "press" } "up" { "release" } default { "click" } } }
            Post-J "/mouse" @{ button = $btn; action = $act } | ConvertTo-Json -Depth 5
        }
        "cursor" {
            if ($Rest.Count -lt 2) { throw "usage: mc.ps1 cursor <x> <y>" }
            Post-J "/cursor" @{ x = [double]$Rest[0]; y = [double]$Rest[1] } | ConvertTo-Json -Depth 5
        }
        "clickat" {
            if ($Rest.Count -lt 2) { throw "usage: mc.ps1 clickat <x> <y> [left|right|middle]" }
            $btn = "left"
            if ($Rest.Count -ge 3) { $btn = $Rest[2] }
            Post-J "/clickAt" @{ x = [double]$Rest[0]; y = [double]$Rest[1]; button = $btn } | ConvertTo-Json -Depth 5
        }
        "virtual" {
            if ($Rest.Count -lt 1) { throw "usage: mc.ps1 virtual <on|off> [guiOnly|all]" }
            $o = @{ enabled = ($Rest[0] -ne "off") }
            if ($Rest.Count -ge 2) { $o.guiOnly = ($Rest[1] -ne "all") }
            Post-J "/virtual" $o | ConvertTo-Json -Depth 5
        }
        "raw" {
            # escape hatch for new endpoints: mc.ps1 raw post /endpoint {"a":1}
            if ($Rest.Count -lt 2) { throw 'usage: mc.ps1 raw <get|post> <path> [json-body]' }
            $verb = $Rest[0].ToLower(); $path = $Rest[1]
            if ($path -notlike "/*") { $path = "/$path" }
            if ($verb -eq "get") { Get-J $path | ConvertTo-Json -Depth 8 }
            else {
                $body = if ($Rest.Count -ge 3) { $Rest[2] } else { "{}" }
                Post-J $path ($body | ConvertFrom-Json) | ConvertTo-Json -Depth 8
            }
        }
        "scroll" {
            if ($Rest.Count -lt 1) { throw "usage: mc.ps1 scroll <dy> [dx]" }
            $o = @{ dy = [double]$Rest[0] }
            if ($Rest.Count -ge 2) { $o.dx = [double]$Rest[1] }
            Post-J "/scroll" $o | ConvertTo-Json -Depth 5
        }
        "chat" {
            if ($Rest.Count -lt 1) { throw 'usage: mc.ps1 chat "<message>"' }
            Post-J "/chat" @{ message = ($Rest -join " ") } | ConvertTo-Json -Depth 5
        }
        "hotbar" {
            if ($Rest.Count -lt 1) { throw "usage: mc.ps1 hotbar <0-8>" }
            Post-J "/hotbar" @{ slot = [int]$Rest[0] } | ConvertTo-Json -Depth 5
        }
        "gui" {
            if ($Rest.Count -lt 1) { throw "usage: mc.ps1 gui <inventory|chat|close> [initial text]" }
            if ($Rest[0] -eq "close") {
                Post-J "/gui" @{ close = $true } | ConvertTo-Json -Depth 5
            } else {
                $txt = ""
                if ($Rest.Count -ge 2) { $txt = $Rest[1] }
                Post-J "/gui" @{ open = $Rest[0]; text = $txt } | ConvertTo-Json -Depth 5
            }
        }
        "focus" { Post-J "/focus" @{} | ConvertTo-Json -Depth 5 }
        "pause" {
            if ($Rest.Count -lt 1) { throw "usage: mc.ps1 pause <on|off>" }
            Post-J "/pause" @{ enabled = ($Rest[0] -eq "on") } | ConvertTo-Json -Depth 5
        }
        default {
            "AI Pilot helper - bridge on $Base"
            "  health | state | shot <out.png> | focus"
            "  look <yaw> <pitch> [rel] | move <forward|back|left|right|jump|sneak|sprint> <down|up>"
            "  key <NAME> [tap|down|up] | char <text> | click [left|right|middle] [click|down|up]"
            "  cursor <x> <y> | clickat <x> <y> [button] | virtual <on|off> [guiOnly|all]"
            "  scroll <dy> | chat <msg> | hotbar <0-8> | gui <inventory|chat|close> [text]"
            "  pause <on|off> | raw <get|post> <path> [json]"
            "  options: -Port 8765 -Token <t> -Base <url>"
        }
    }
} catch {
    "ERROR: $($_.Exception.Message)"
    if ($_.Exception.Message -match "refused|connect|unable") {
        "  -> is Minecraft 1.20.6 running with the aipilot mod installed?"
        "     see the repo README for build/install steps"
    }
    exit 1
}
