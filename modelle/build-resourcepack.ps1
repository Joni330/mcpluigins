$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$pack = Join-Path $root 'resourcepack'
$utf8 = New-Object System.Text.UTF8Encoding($false)
function Write-Json($path, $value) {
    [System.IO.File]::WriteAllText($path, ($value | ConvertTo-Json -Depth 100), $utf8)
}
@('assets/casino/models/item', 'assets/casino/items', 'assets/casino/textures/item') | ForEach-Object {
    New-Item -ItemType Directory -Path (Join-Path $pack $_) -Force | Out-Null
}
$model = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'wechsler.json') -Raw | ConvertFrom-Json
$model.textures.'0' = 'casino:item/wechsler'
$model.textures.particle = 'casino:item/wechsler'
$display = @{
    gui = @{rotation=@(30,225,0); translation=@(0,-3,0); scale=@(.45,.45,.45)}
    ground = @{translation=@(0,3,0); scale=@(.25,.25,.25)}
    fixed = @{translation=@(0,-3,0); scale=@(.45,.45,.45)}
    thirdperson_righthand = @{rotation=@(75,45,0); translation=@(0,1,0); scale=@(.3,.3,.3)}
    thirdperson_lefthand = @{rotation=@(75,45,0); translation=@(0,1,0); scale=@(.3,.3,.3)}
    firstperson_righthand = @{rotation=@(0,45,0); translation=@(0,-2,0); scale=@(.35,.35,.35)}
    firstperson_lefthand = @{rotation=@(0,225,0); translation=@(0,-2,0); scale=@(.35,.35,.35)}
}
$model | Add-Member -MemberType NoteProperty -Name display -Value $display -Force
Write-Json (Join-Path $pack 'assets/casino/models/item/wechsler.json') $model
Write-Json (Join-Path $pack 'assets/casino/items/wechsler.json') @{model=@{type='minecraft:model'; model='casino:item/wechsler'}}
Write-Json (Join-Path $pack 'pack.mcmeta') @{pack=@{description='Casino - Wechselautomat'; min_format=@(97,1); max_format=@(97,1)}}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'wechsler.png') -Destination (Join-Path $pack 'assets/casino/textures/item/wechsler.png') -Force
$slotModel = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'spielautomat.json') -Raw | ConvertFrom-Json
foreach ($property in $slotModel.textures.PSObject.Properties) { $property.Value = 'casino:item/spielautomat' }
$slotDisplay = $display.Clone()
$slotDisplay.gui = @{rotation=@(30,135,0); translation=@(0,-3,0); scale=@(.45,.45,.45)}
$slotModel | Add-Member -MemberType NoteProperty -Name display -Value $slotDisplay -Force
Write-Json (Join-Path $pack 'assets/casino/models/item/spielautomat.json') $slotModel
Write-Json (Join-Path $pack 'assets/casino/items/spielautomat.json') @{model=@{type='minecraft:model'; model='casino:item/spielautomat'}}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'spielautomat.png') -Destination (Join-Path $pack 'assets/casino/textures/item/spielautomat.png') -Force
foreach ($variant in @(
    @{Source='chip_rot'; Target='chip_rot'},
    @{Source=('chip_gr' + [char]0x00fc + 'n'); Target='chip_gruen'},
    @{Source='chip_blau'; Target='chip_blau'},
    @{Source='chip_lila'; Target='chip_lila'}
)) {
$chipName = $variant.Target
$chip = Get-Content -LiteralPath (Join-Path $PSScriptRoot ($variant.Source + '.json')) -Raw | ConvertFrom-Json
foreach ($property in $chip.textures.PSObject.Properties) { $property.Value = ('casino:item/' + $chipName) }
$chipDisplay = @{
    gui = @{rotation=@(-90,0,180); translation=@(0,0,0); scale=@(1.7,1.7,1.7)}
    ground = @{translation=@(0,3,0); scale=@(.7,.7,.7)}
    fixed = @{rotation=@(90,0,0); translation=@(0,0,0); scale=@(1,1,1)}
    thirdperson_righthand = @{rotation=@(0,0,0); translation=@(0,4,0); scale=@(.7,.7,.7)}
    thirdperson_lefthand = @{rotation=@(0,0,0); translation=@(0,4,0); scale=@(.7,.7,.7)}
    firstperson_righthand = @{rotation=@(0,0,0); translation=@(0,4,0); scale=@(1,1,1)}
    firstperson_lefthand = @{rotation=@(0,0,0); translation=@(0,4,0); scale=@(1,1,1)}
}
$chip | Add-Member -MemberType NoteProperty -Name display -Value $chipDisplay -Force
Write-Json (Join-Path $pack ('assets/casino/models/item/' + $chipName + '.json')) $chip
Write-Json (Join-Path $pack ('assets/casino/items/' + $chipName + '.json')) @{model=@{type='minecraft:model'; model=('casino:item/' + $chipName)}}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot ($variant.Source + '.png')) -Destination (Join-Path $pack ('assets/casino/textures/item/' + $chipName + '.png')) -Force
}
Compress-Archive -Path (Join-Path $pack '*') -DestinationPath (Join-Path $root 'Casino-Resourcepack.zip') -Force
Write-Host 'Casino-Resourcepack.zip erstellt.'
