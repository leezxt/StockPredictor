param(
    [ValidateSet('app-image', 'exe', 'both')]
    [string]$Mode = 'app-image',

    [string]$ProjectName = 'StockPredictor',

    [string]$MainJar = '',

    [string]$MainClass = 'org.springframework.boot.loader.launch.JarLauncher',

    [string]$DisplayName = 'StockPredictor',

    [string]$Vendor = 'GTalent',

    [string]$OutputDir = 'dist',

    [bool]$SkipTests = $true,

    [bool]$CreateZip = $true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$targetDir = Join-Path $scriptRoot 'target'
$destDir = Join-Path $scriptRoot $OutputDir
$pomPath = Join-Path $scriptRoot 'pom.xml'
$assetsDir = Join-Path $scriptRoot 'packaging-assets'
$iconPath = Join-Path $assetsDir 'stockpredictor.ico'
$defaultDescription = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('U3RvY2tQcmVkaWN0b3Ig6IKh56Wo5YiG5p6Q57O757Wx'))

function T([string]$Base64Text) {
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Base64Text))
}

function Ensure-Tool([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw ((T '57y65bCR5b+F6KaB5bel5YW377ya') + $Name)
    }
}

function Resolve-MainJar([string]$JarName) {
    if ($JarName) {
        $jarPath = Join-Path $targetDir $JarName
        if (-not (Test-Path $jarPath)) {
            throw ((T '5om+5LiN5Yiw5Li756iL5byPIEpBUu+8mg==') + $jarPath)
        }
        return $JarName
    }

    $candidate = Get-ChildItem -Path $targetDir -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $candidate) {
        throw (T '5ZyoIHRhcmdldC8g5om+5LiN5Yiw5bey5bCB6KOd55qEIEpBUu+8jOWPr+iDveaYryBNYXZlbiDlu7rnva7lpLHmlZfjgII=')
    }

    return $candidate.Name
}

function Resolve-ProjectVersion {
    if (-not (Test-Path $pomPath)) {
        return 'unknown'
    }

    try {
        [xml]$pom = Get-Content -Path $pomPath
        $versionNode = $pom.project.version
        if ($versionNode) {
            return [string]$versionNode
        }
    }
    catch {
        Write-Warning ((T '54Sh5rOV5b6eIHBvbS54bWwg6K6A5Y+W54mI5pys6Jmf77ya') + $_.Exception.Message)
    }

    return 'unknown'
}

function Resolve-PackageVersion([string]$Version) {
    $matches = [regex]::Matches($Version, '\d+')
    if (-not $matches -or $matches.Count -eq 0) {
        return '1.0.0'
    }

    $parts = @()
    foreach ($match in $matches) {
        $parts += $match.Value
        if ($parts.Count -ge 3) {
            break
        }
    }

    return ($parts -join '.')
}

function Get-WixBinDirectory {
    $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
    $light = Get-Command light.exe -ErrorAction SilentlyContinue
    if ($candle -and $light) {
        return Split-Path -Parent $candle.Source
    }

    $commonDirs = @(
        'C:\Program Files (x86)\WiX Toolset v3.14\bin',
        'C:\Program Files\WiX Toolset v3.14\bin',
        'C:\Program Files (x86)\WiX Toolset v3.11\bin',
        'C:\Program Files\WiX Toolset v3.11\bin'
    )

    foreach ($dir in $commonDirs) {
        if ((Test-Path (Join-Path $dir 'candle.exe')) -and (Test-Path (Join-Path $dir 'light.exe'))) {
            return $dir
        }
    }

    return $null
}

function Ensure-WixOnPath {
    $wixBin = Get-WixBinDirectory
    if (-not $wixBin) {
        return $false
    }

    $pathEntries = ($env:Path -split ';') | Where-Object { $_ }
    if ($pathEntries -notcontains $wixBin) {
        $env:Path = "$wixBin;$env:Path"
    }

    return $true
}

function Ensure-AppIcon {
    if (-not (Test-Path $assetsDir)) {
        New-Item -ItemType Directory -Path $assetsDir | Out-Null
    }

    if (Test-Path $iconPath) {
        return $iconPath
    }

    Write-Host (T '5q2j5Zyo5bu656uL5oeJ55So56iL5byP5ZyW56S6Li4u')

    Add-Type -AssemblyName System.Drawing
    $drawingAssembly = [System.Drawing.Bitmap].Assembly.Location
    Add-Type -ReferencedAssemblies $drawingAssembly -Language CSharp @"
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

public static class StockPredictorIconGenerator
{
    public static void Create(string outputPath)
    {
        const int size = 256;
        using (var bitmap = new Bitmap(size, size, PixelFormat.Format32bppArgb))
        using (var graphics = Graphics.FromImage(bitmap))
        {
            graphics.SmoothingMode = SmoothingMode.AntiAlias;
            graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
            graphics.Clear(Color.Transparent);

            using (var background = new GraphicsPath())
            {
                int radius = 54;
                background.AddArc(18, 18, radius, radius, 180, 90);
                background.AddArc(size - 18 - radius, 18, radius, radius, 270, 90);
                background.AddArc(size - 18 - radius, size - 18 - radius, radius, radius, 0, 90);
                background.AddArc(18, size - 18 - radius, radius, radius, 90, 90);
                background.CloseFigure();

                using (var brush = new LinearGradientBrush(new Rectangle(0, 0, size, size), Color.FromArgb(15, 30, 74), Color.FromArgb(17, 94, 89), 45f))
                {
                    graphics.FillPath(brush, background);
                }
            }

            using (var glowBrush = new SolidBrush(Color.FromArgb(35, 255, 255, 255)))
            {
                graphics.FillEllipse(glowBrush, 24, 22, 208, 96);
            }

            PointF[] linePoints = new[] {
                new PointF(46, 170),
                new PointF(88, 132),
                new PointF(118, 148),
                new PointF(154, 98),
                new PointF(188, 118),
                new PointF(214, 72)
            };

            using (var fillPath = new GraphicsPath())
            {
                fillPath.AddLines(linePoints);
                fillPath.AddLine(214, 72, 214, 206);
                fillPath.AddLine(214, 206, 46, 206);
                fillPath.CloseFigure();

                using (var fillBrush = new LinearGradientBrush(new Rectangle(40, 72, 180, 140), Color.FromArgb(110, 34, 211, 238), Color.FromArgb(30, 34, 211, 238), 90f))
                {
                    graphics.FillPath(fillBrush, fillPath);
                }
            }

            using (var gridPen = new Pen(Color.FromArgb(30, 255, 255, 255), 3f))
            {
                for (int i = 0; i < 4; i++)
                {
                    float y = 72 + i * 34;
                    graphics.DrawLine(gridPen, 40, y, 216, y);
                }
            }

            using (var linePen = new Pen(Color.FromArgb(80, 236, 253, 245), 12f))
            {
                linePen.StartCap = LineCap.Round;
                linePen.EndCap = LineCap.Round;
                linePen.LineJoin = LineJoin.Round;
                graphics.DrawLines(linePen, linePoints);
            }

            using (var accentBrush = new SolidBrush(Color.FromArgb(255, 248, 113, 113)))
            {
                foreach (var point in linePoints)
                {
                    graphics.FillEllipse(accentBrush, point.X - 8, point.Y - 8, 16, 16);
                }
            }

            using (var textBrush = new SolidBrush(Color.FromArgb(245, 255, 255, 255)))
            using (var font = new Font("Segoe UI", 56, FontStyle.Bold, GraphicsUnit.Pixel))
            {
                var format = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
                graphics.DrawString("\u53F0", font, textBrush, new RectangleF(20, 18, 216, 72), format);
            }

            using (var stream = new FileStream(outputPath, FileMode.Create, FileAccess.Write))
            using (var writer = new BinaryWriter(stream))
            using (var pngStream = new MemoryStream())
            {
                bitmap.Save(pngStream, ImageFormat.Png);
                byte[] pngBytes = pngStream.ToArray();

                writer.Write((ushort)0);
                writer.Write((ushort)1);
                writer.Write((ushort)1);
                writer.Write((byte)0);
                writer.Write((byte)0);
                writer.Write((byte)0);
                writer.Write((byte)0);
                writer.Write((ushort)1);
                writer.Write((ushort)32);
                writer.Write(pngBytes.Length);
                writer.Write(22);
                writer.Write(pngBytes);
            }
        }
    }
}
"@

    [StockPredictorIconGenerator]::Create($iconPath)
    return $iconPath
}

function Invoke-MavenBuild {
    $args = @('clean', 'package')
    if ($SkipTests) {
        $args += '-DskipTests'
    }

    Write-Host (T 'WzEvM10g5q2j5Zyo5bu6572uIFNwcmluZyBCb290IEpBUi4uLg==')
    & mvn @args
    if ($LASTEXITCODE -ne 0) {
        throw ((T 'TWF2ZW4g5bu6572u5aSx5pWX77yM57WQ5p2f5Luj56K877ya') + $LASTEXITCODE)
    }
}

function New-PortableZip([string]$AppImageDir, [string]$Version) {
    if (-not $CreateZip) {
        return
    }

    $safeVersion = ($Version -replace '[^A-Za-z0-9._-]', '-')
    $zipName = if ($safeVersion -and $safeVersion -ne 'unknown') {
        "$ProjectName-$safeVersion-windows-portable.zip"
    }
    else {
        "$ProjectName-windows-portable.zip"
    }
    $zipPath = Join-Path $destDir $zipName

    if (Test-Path $zipPath) {
        Remove-Item -Path $zipPath -Force
    }

    Write-Host (T 'WzMvM10g5q2j5Zyo5bu656uL5Y+v5pSc54mIIFpJUCDlo5PnuK7mqpQuLi4=')
    Compress-Archive -Path (Join-Path $AppImageDir '*') -DestinationPath $zipPath -CompressionLevel Optimal
    Write-Host ((T 'WklQIOWjk+e4ruaqlOW3suW7uueri++8mg==') + $zipPath)
}

function Build-AppImage([string]$JarName, [string]$ProjectVersion, [string]$PackageVersion) {
    $appImageDir = Join-Path $destDir $ProjectName
    $resolvedIconPath = Ensure-AppIcon
    if (Test-Path $appImageDir) {
        Remove-Item -Path $appImageDir -Recurse -Force
    }

    Write-Host (T 'WzIvM10g5q2j5Zyo5bu656uLIFdpbmRvd3Mg5Y+v5pSc54mILi4u')
    & jpackage `
        --type app-image `
        --name $ProjectName `
        --app-version $PackageVersion `
        --vendor $Vendor `
        --description $defaultDescription `
        --icon $resolvedIconPath `
        --java-options "-Dapp.browser.auto-open=true" `
        --java-options "-Dapp.browser.startup-delay-ms=1500" `
        --input $targetDir `
        --main-jar $JarName `
        --main-class $MainClass `
        --dest $destDir `
        --win-console

    if ($LASTEXITCODE -ne 0) {
        throw ((T 'anBhY2thZ2Ug5bu656uL5Y+v5pSc54mI5aSx5pWX77yM57WQ5p2f5Luj56K877ya') + $LASTEXITCODE)
    }

    Write-Host ((T '5Y+v5pSc54mI5bey5a6M5oiQ77ya') + (Join-Path $destDir $ProjectName))
    New-PortableZip -AppImageDir $appImageDir -Version $ProjectVersion
}

function Build-ExeInstaller([string]$JarName, [string]$PackageVersion) {
    $wixReady = Ensure-WixOnPath
    $hasCandle = Get-Command candle.exe -ErrorAction SilentlyContinue
    $hasLight = Get-Command light.exe -ErrorAction SilentlyContinue
    $resolvedIconPath = Ensure-AppIcon

    if (-not $wixReady -or -not $hasCandle -or -not $hasLight) {
        Write-Warning (T '5om+5LiN5YiwIFdpWCBUb29sc2V077yIY2FuZGxlLmV4ZSAvIGxpZ2h0LmV4Ze+8ie+8jOeVpemBjuWuieijneeJiCBFWEUg5bu656uL44CC')
        Write-Warning (T '6KuL5YWI5a6J6KOdIFdpWCBUb29sc2V0IDMueO+8jOS5i+W+jOWGjemHjeaWsOWft+ihjCAtTW9kZSBleGUg5oiWIC1Nb2RlIGJvdGjjgII=')
        return $false
    }

    Get-ChildItem -Path $destDir -Filter "*.exe" -ErrorAction SilentlyContinue |
        Remove-Item -Force -ErrorAction SilentlyContinue

    Write-Host (T 'WzMvM10g5q2j5Zyo5bu656uLIFdpbmRvd3Mg5a6J6KOd54mIIEVYRS4uLg==')
    & jpackage `
        --type exe `
        --name $ProjectName `
        --app-version $PackageVersion `
        --vendor $Vendor `
        --description $defaultDescription `
        --icon $resolvedIconPath `
        --java-options "-Dapp.browser.auto-open=true" `
        --java-options "-Dapp.browser.startup-delay-ms=1500" `
        --input $targetDir `
        --main-jar $JarName `
        --main-class $MainClass `
        --dest $destDir `
        --win-shortcut `
        --win-menu `
        --win-console

    if ($LASTEXITCODE -ne 0) {
        throw ((T 'anBhY2thZ2Ug5bu656uL5a6J6KOd54mIIEVYRSDlpLHmlZfvvIzntZDmnZ/ku6PnorzvvJo=') + $LASTEXITCODE)
    }

    Write-Host ((T '5a6J6KOd54mI5bey5a6M5oiQ77ya') + $destDir)
    return $true
}

Push-Location $scriptRoot
try {
    Ensure-Tool 'mvn'
    Ensure-Tool 'jpackage'
    $null = Ensure-AppIcon

    if (-not (Test-Path $destDir)) {
        New-Item -ItemType Directory -Path $destDir | Out-Null
    }

    Invoke-MavenBuild
    $resolvedJar = Resolve-MainJar -JarName $MainJar
    $projectVersion = Resolve-ProjectVersion
    $packageVersion = Resolve-PackageVersion -Version $projectVersion

    switch ($Mode) {
        'app-image' {
            Build-AppImage -JarName $resolvedJar -ProjectVersion $projectVersion -PackageVersion $packageVersion
        }
        'exe' {
            $built = Build-ExeInstaller -JarName $resolvedJar -PackageVersion $packageVersion
            if (-not $built) {
                exit 2
            }
        }
        'both' {
            Build-AppImage -JarName $resolvedJar -ProjectVersion $projectVersion -PackageVersion $packageVersion
            $built = Build-ExeInstaller -JarName $resolvedJar -PackageVersion $packageVersion
            if (-not $built) {
                exit 2
            }
        }
    }

    Write-Host (T '5bCB6KOd5a6M5oiQ44CC')
}
finally {
    Pop-Location
}


