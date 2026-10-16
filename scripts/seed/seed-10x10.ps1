<#
텐바이텐 검색 결과(ItemList JSON-LD) → 상품 페이지(Product/BreadcrumbList JSON-LD)를 읽어
POST /api/admin/products/bulk 로 적재한다. 임베딩/Qdrant 업서트는 앱의 기존 파이프라인이 처리한다.

- robots.txt(User-agent: *) 허용 경로(/search/, /shopping/)만 요청하고, 요청 간 DelayMs 만큼 쉰다.
- 로컬 평가용 카탈로그 구축 용도. 수집 데이터를 외부에 재배포하지 않는다.

사용법 (앱을 local 프로파일로 먼저 기동):
  .\scripts\seed\seed-10x10.ps1            # 적재
  .\scripts\seed\seed-10x10.ps1 -DryRun    # 수집만 하고 JSON 파일로 저장
#>
param(
    [string]$KeywordsFile = "$PSScriptRoot\keywords.txt",
    [string]$ApiBase = 'http://localhost:8080',
    [int]$DelayMs = 1000,
    [int]$BatchSize = 100,
    [int]$MaxKeywordsPerProduct = 10,
    [string]$OutFile = "$PSScriptRoot\..\..\build\seed-10x10.json",
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$ua = 'Mozilla/5.0 (giftrecommender-seed; local evaluation)'
$tmp = [IO.Path]::GetTempFileName()

function Get-Html([string]$url) {
    Start-Sleep -Milliseconds $DelayMs
    $script:requests++
    curl.exe -sL --max-time 20 -A $ua -o $tmp $url
    if ($LASTEXITCODE -ne 0) { return $null }
    [IO.File]::ReadAllText($tmp, [Text.Encoding]::UTF8)
}

function Get-JsonLd([string]$html, [string]$type) {
    foreach ($m in [regex]::Matches($html, '<script type="application/ld\+json">([\s\S]*?)</script>')) {
        try { $o = $m.Groups[1].Value | ConvertFrom-Json } catch { continue }
        if ($o.'@type' -eq $type) { return $o }
    }
}

function Cut([string]$s, [int]$n) { if ($s -and $s.Length -gt $n) { $s.Substring(0, $n) } else { $s } }

$keywords = Get-Content $KeywordsFile -Encoding utf8 | ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith('#') }
$byId = [ordered]@{}
$requests = 0; $skipped = 0

foreach ($kw in $keywords) {
    $list = Get-JsonLd (Get-Html "https://www.10x10.co.kr/search/renewal/index.asp?rect=$([uri]::EscapeDataString($kw))") 'ItemList'
    $new = 0
    foreach ($el in @($list.itemListElement)) {
        if ($el.url -notmatch 'itemid=(\d+)') { continue }
        $id = $Matches[1]
        if ($byId.Contains($id)) {
            # 여러 키워드에 걸린 상품은 검색 키워드를 합친다 (평가셋 정답 후보 추적용)
            if ($byId[$id].keywords -notcontains $kw) { $byId[$id].keywords = @($kw) + $byId[$id].keywords }
            continue
        }
        $html = Get-Html $el.url
        $p = if ($html) { Get-JsonLd $html 'Product' }
        if (-not $p -or -not $p.offers.price -or -not $p.image) { $skipped++; continue }
        $crumb = Get-JsonLd $html 'BreadcrumbList'
        $category = (@($crumb.itemListElement) | Where-Object { $_.name -ne '홈' } | ForEach-Object { $_.name }) -join '>'
        $productKeywords = @("$($p.keywords)" -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Select-Object -First $MaxKeywordsPerProduct)

        $byId[$id] = [ordered]@{
            originalName = Cut $p.name 255
            price        = [int]$p.offers.price
            imageUrl     = $p.image
            productUrl   = "https://www.10x10.co.kr/shopping/category_prd.asp?itemid=$id"
            category     = Cut $category 100
            keywords     = @(@($kw) + $productKeywords | Select-Object -Unique)
            reviewCount  = 0
            sellerName   = Cut $p.brand.name 100
            platform     = '텐바이텐'
            gender       = 'ANY'
            age          = 'NONE'
            isAdvertised = $false
        }
        $new++
    }
    Write-Host ("[{0}] listed={1} new={2} total={3}" -f $kw, @($list.itemListElement).Count, $new, $byId.Count)
}

$items = @($byId.Values)
New-Item -ItemType Directory -Force (Split-Path $OutFile) | Out-Null
[IO.File]::WriteAllText($OutFile, (ConvertTo-Json -InputObject $items -Depth 5), (New-Object Text.UTF8Encoding $false))
Write-Host ("keywords={0} requests={1} products={2} skipped={3} -> {4}" -f $keywords.Count, $requests, $items.Count, $skipped, $OutFile)
if ($DryRun) { return }

$sum = @{ total = 0; success = 0; duplicated = 0 }
for ($i = 0; $i -lt $items.Count; $i += $BatchSize) {
    $chunk = $items[$i..([Math]::Min($i + $BatchSize, $items.Count) - 1)]
    $body = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject @($chunk) -Depth 5 -Compress))
    $res = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/admin/products/bulk" -ContentType 'application/json; charset=utf-8' -Body $body
    foreach ($k in @('total', 'success', 'duplicated')) { $sum[$k] += $res.data.summary.$k }
    Write-Host ("batch {0}-{1}: success={2} duplicated={3}" -f $i, ($i + $chunk.Count - 1), $res.data.summary.success, $res.data.summary.duplicated)
}
Write-Host ("DONE total={0} success={1} duplicated={2} failed={3}" -f $sum.total, $sum.success, $sum.duplicated, ($sum.total - $sum.success - $sum.duplicated))
