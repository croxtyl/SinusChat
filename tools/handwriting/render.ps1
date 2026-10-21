param([string]$spec, [string]$png, [int]$w = 1400, [int]$h = 1800)
Add-Type -AssemblyName PresentationCore, PresentationFramework, WindowsBase
$dv = New-Object System.Windows.Media.DrawingVisual
$dc = $dv.RenderOpen()
$bg = New-Object System.Windows.Media.SolidColorBrush ([System.Windows.Media.Color]::FromRgb(18, 20, 28))
$dc.DrawRectangle($bg, $null, (New-Object System.Windows.Rect(0, 0, $w, $h)))
$face = New-Object System.Windows.Media.Typeface "Segoe UI"
foreach ($line in [System.IO.File]::ReadAllLines($spec)) {
  if ($line.Trim() -eq "") { continue }
  $p = $line.Split('|')
  $x = [double]$p[0]; $y = [double]$p[1]; $s = [double]$p[2]; $sw = [double]$p[3]; $col = $p[4]; $data = $p[5]
  if ($col -eq "text") {
    $ft = New-Object System.Windows.Media.FormattedText($data, [System.Globalization.CultureInfo]::InvariantCulture, [System.Windows.FlowDirection]::LeftToRight, $face, $sw, [System.Windows.Media.Brushes]::Gray)
    $dc.DrawText($ft, (New-Object System.Windows.Point($x, $y)))
    continue
  }
  $geo = [System.Windows.Media.Geometry]::Parse($data)
  $tg = New-Object System.Windows.Media.TransformGroup
  $tg.Children.Add((New-Object System.Windows.Media.ScaleTransform($s, $s)))
  $tg.Children.Add((New-Object System.Windows.Media.TranslateTransform($x, $y)))
  $geo = $geo.Clone(); $geo.Transform = $tg
  $brush = New-Object System.Windows.Media.SolidColorBrush ([System.Windows.Media.ColorConverter]::ConvertFromString($col))
  $pen = New-Object System.Windows.Media.Pen($brush, $sw)
  $pen.StartLineCap = 'Round'; $pen.EndLineCap = 'Round'; $pen.LineJoin = 'Round'
  if ($p.Length -gt 6 -and $p[6] -eq "fill") { $dc.DrawGeometry($brush, $null, $geo) } else { $dc.DrawGeometry($null, $pen, $geo) }
}
$dc.Close()
$rtb = New-Object System.Windows.Media.Imaging.RenderTargetBitmap($w, $h, 96, 96, [System.Windows.Media.PixelFormats]::Pbgra32)
$rtb.Render($dv)
$enc = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
$enc.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($rtb))
$fs = [System.IO.File]::Create($png); $enc.Save($fs); $fs.Close()
"ok $png"
