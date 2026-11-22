<?php
/**
 * Wolf Wheels · cuentas, colección y fotos de respaldo.
 *
 * Instalación: sube esta carpeta (api.php + .htaccess) a /public_html/wolf-wheels/ de tu hosting.
 * config.php (NO va en GitHub), opcional:
 *   <?php const EBAY_CLIENT_ID = '…'; const EBAY_CLIENT_SECRET = '…'; const MAIL_FROM = 'Wolf Wheels <no-reply@tudominio>';
 * La carpeta "data" se crea sola y queda protegida.
 *
 * Acciones (?a=…):
 *   register  POST {name,email,password}  → {pending:true}  (manda un código de 6 cifras al email)
 *   verify    POST {email,code}           → {token,name,email}  (activa la cuenta)
 *   resend    POST {email}                → {ok}  (vuelve a mandar el código)
 *   login     POST {email,password}       → {token,name,email}  ·  403 unverified si falta verificar
 *   me        GET  (Authorization: Bearer)
 *   logout    POST (Bearer)
 *   col       GET  (Bearer) → {items}   ·   POST {items} → mezcla por elemento (gana el cambio más reciente) → {items}
 *   img       GET  q=texto → {url}  (foto de un anuncio de eBay, con caché de 30 días)
 */

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

const APP_KEY = 'wolfwheels-v1';
if (is_file(__DIR__ . '/config.php')) require __DIR__ . '/config.php';

function out($data, int $code = 200) { http_response_code($code); echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES); exit; }
function fail(int $code, string $msg) { out(['error' => $msg], $code); }

if (($_SERVER['HTTP_X_WOLF_APP'] ?? '') !== APP_KEY) fail(403, 'app');

$dir = __DIR__ . '/data';
foreach ([$dir, "$dir/col", "$dir/img"] as $d) if (!is_dir($d)) mkdir($d, 0700, true);
if (!is_file("$dir/.htaccess")) { file_put_contents("$dir/.htaccess", "Require all denied\nDeny from all\n"); file_put_contents("$dir/index.html", ''); }

/** Lee y escribe un JSON con bloqueo (para que dos aparatos a la vez no se pisen). */
function locked(string $file, callable $fn) {
    $fp = fopen($file, 'c+');
    if (!$fp) fail(500, 'io');
    flock($fp, LOCK_EX);
    $raw = stream_get_contents($fp);
    $data = $raw ? json_decode($raw, true) : [];
    if (!is_array($data)) $data = [];
    [$data2, $result] = $fn($data);
    if ($data2 !== null) {
        ftruncate($fp, 0); rewind($fp);
        fwrite($fp, json_encode($data2, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        fflush($fp);
    }
    flock($fp, LOCK_UN); fclose($fp);
    return $result;
}

function body(): array {
    $raw = file_get_contents('php://input');
    if (strlen($raw) > 2 * 1024 * 1024) fail(413, 'too_big');
    $j = json_decode($raw, true);
    return is_array($j) ? $j : [];
}

function newToken(string $uid): string {
    global $dir;
    $token = bin2hex(random_bytes(32));
    locked("$dir/tokens.json", function ($t) use ($token, $uid) { $t[hash('sha256', $token)] = ['u' => $uid, 't' => time()]; return [$t, null]; });
    return $token;
}

function auth(): array {
    global $dir;
    $h = $_SERVER['HTTP_AUTHORIZATION'] ?? ($_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? ($_SERVER['HTTP_X_WOLF_TOKEN'] ?? ''));
    $token = trim(preg_replace('/^Bearer\s+/i', '', $h));
    if (!preg_match('/^[a-f0-9]{64}$/', $token)) fail(401, 'login');
    $tokens = json_decode(@file_get_contents("$dir/tokens.json") ?: '[]', true) ?: [];
    $row = $tokens[hash('sha256', $token)] ?? null;
    if (!$row) fail(401, 'login');
    $users = json_decode(@file_get_contents("$dir/users.json") ?: '[]', true) ?: [];
    foreach ($users as $email => $u) if ($u['id'] === $row['u']) return $u + ['email' => $email, 'token' => $token];
    fail(401, 'login');
}

/** Frena los intentos repetidos de contraseña: 10 fallos en 15 minutos bloquean ese email un rato. */
function tooMany(string $email, bool $failed = false): bool {
    global $dir;
    return locked("$dir/attempts.json", function ($a) use ($email, $failed) {
        $now = time(); $k = hash('sha256', $email);
        $list = array_values(array_filter($a[$k] ?? [], fn($t) => $t > $now - 900));
        if ($failed) $list[] = $now;
        $a[$k] = $list;
        return [$a, count($list) >= 10];
    });
}

/** Crea un código nuevo para ese email, lo guarda (cifrado) y lo manda por correo. */
function sendCode(string $email, string $name): string {
    global $dir;
    $code = str_pad((string)random_int(0, 999999), 6, '0', STR_PAD_LEFT);
    $ok = locked("$dir/users.json", function ($users) use ($email, $code) {
        if (!isset($users[$email])) return [null, 'fail'];
        $u = $users[$email];
        $now = time();
        $sent = array_values(array_filter($u['sent'] ?? [], fn($t) => $t > $now - 3600));
        if (count($sent) >= 6 || (end($sent) ?: 0) > $now - 45) return [null, 'rate'];   // máx. 6 por hora, 1 cada 45 s
        $sent[] = $now;
        $u['code'] = password_hash($code, PASSWORD_DEFAULT);
        $u['codeExp'] = $now + 1800;   // 30 minutos
        $u['codeTries'] = 0;
        $u['sent'] = $sent;
        $users[$email] = $u;
        return [$users, 'ok'];
    });
    if ($ok !== 'ok') return $ok;
    $host = $_SERVER['HTTP_HOST'] ?? 'jonatanmontero.es';
    $from = defined('MAIL_FROM') ? MAIL_FROM : "Wolf Wheels <no-reply@$host>";
    $subject = '=?UTF-8?B?' . base64_encode("Tu código de Wolf Wheels: $code") . '?=';
    $safe = htmlspecialchars($name, ENT_QUOTES, 'UTF-8');
    $html = "<div style=\"font-family:Arial,sans-serif;background:#0B0C0F;color:#F4F4F5;padding:28px;border-radius:14px;max-width:480px\">"
        . "<div style=\"font-size:22px;font-weight:800;font-style:italic\">WOLF <span style=\"color:#FF7A1A\">WHEELS</span></div>"
        . "<p style=\"color:#A3A7B0\">Hola $safe, este es tu código para activar la cuenta:</p>"
        . "<div style=\"font-size:36px;font-weight:800;letter-spacing:8px;color:#FFB81C;margin:18px 0\">$code</div>"
        . "<p style=\"color:#6B707A;font-size:13px\">Caduca en 30 minutos. Si no has creado una cuenta en Wolf Wheels, ignora este correo.</p></div>";
    $headers = "MIME-Version: 1.0\r\nContent-Type: text/html; charset=UTF-8\r\nFrom: $from\r\n";
    return @mail($email, $subject, $html, $headers) ? 'ok' : 'fail';
}

$a = $_GET['a'] ?? '';
$method = $_SERVER['REQUEST_METHOD'];

switch ($a) {
case 'register': {
    if ($method !== 'POST') fail(405, 'method');
    $b = body();
    $email = strtolower(trim($b['email'] ?? ''));
    $name = mb_substr(trim($b['name'] ?? ''), 0, 40);
    $pass = (string)($b['password'] ?? '');
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) fail(400, 'email');
    if (mb_strlen($pass) < 6) fail(400, 'password');
    if ($name === '') $name = explode('@', $email)[0];
    $r = locked("$dir/users.json", function ($users) use ($email, $name, $pass) {
        $old = $users[$email] ?? null;
        if ($old && !empty($old['verified'])) return [null, 'exists'];
        // Sin verificar: se puede volver a registrar (p. ej. si se equivocó de contraseña)
        $users[$email] = ['id' => $old['id'] ?? bin2hex(random_bytes(12)), 'name' => $name, 'hash' => password_hash($pass, PASSWORD_DEFAULT),
            'created' => $old['created'] ?? time(), 'verified' => false, 'sent' => $old['sent'] ?? []];
        return [$users, 'ok'];
    });
    if ($r === 'exists') fail(409, 'exists');
    $sc = sendCode($email, $name);
    if ($sc === 'fail') fail(502, 'mail');
    out(['pending' => true, 'email' => $email, 'sent' => $sc === 'ok']);
}
case 'verify': {
    if ($method !== 'POST') fail(405, 'method');
    $b = body();
    $email = strtolower(trim($b['email'] ?? ''));
    $code = preg_replace('/\D/', '', (string)($b['code'] ?? ''));
    if (tooMany($email)) fail(429, 'wait');
    $u = locked("$dir/users.json", function ($users) use ($email, $code) {
        $u = $users[$email] ?? null;
        if (!$u) return [null, 'wrong'];
        if (!empty($u['verified'])) return [null, $u];
        if (($u['codeExp'] ?? 0) < time()) return [null, 'expired'];
        if (($u['codeTries'] ?? 0) >= 5) return [null, 'expired'];
        if (!password_verify($code, $u['code'] ?? '')) { $u['codeTries'] = ($u['codeTries'] ?? 0) + 1; $users[$email] = $u; return [$users, 'wrong'];}
        $u['verified'] = true; unset($u['code'], $u['codeExp'], $u['codeTries']);
        $users[$email] = $u;
        return [$users, $u];
    });
    if ($u === 'wrong') { tooMany($email, true); fail(401, 'code'); }
    if ($u === 'expired') fail(410, 'expired');
    out(['token' => newToken($u['id']), 'name' => $u['name'], 'email' => $email]);
}
case 'resend': {
    if ($method !== 'POST') fail(405, 'method');
    $email = strtolower(trim(body()['email'] ?? ''));
    $users = json_decode(@file_get_contents("$dir/users.json") ?: '[]', true) ?: [];
    $u = $users[$email] ?? null;
    if (!$u || !empty($u['verified'])) out(['ok' => true]);
    $sc = sendCode($email, $u['name']);
    if ($sc === 'rate') fail(429, 'mail_wait');
    if ($sc === 'fail') fail(502, 'mail');
    out(['ok' => true]);
}
case 'login': {
    if ($method !== 'POST') fail(405, 'method');
    $b = body();
    $email = strtolower(trim($b['email'] ?? ''));
    $pass = (string)($b['password'] ?? '');
    if (tooMany($email)) fail(429, 'wait');
    $users = json_decode(@file_get_contents("$dir/users.json") ?: '[]', true) ?: [];
    $u = $users[$email] ?? null;
    if (!$u || !password_verify($pass, $u['hash'])) { tooMany($email, true); fail(401, 'wrong'); }
    if (array_key_exists('verified', $u) && !$u['verified']) { sendCode($email, $u['name']); fail(403, 'unverified'); }
    out(['token' => newToken($u['id']), 'name' => $u['name'], 'email' => $email]);
}
case 'me': {
    $u = auth();
    out(['name' => $u['name'], 'email' => $u['email']]);
}
case 'logout': {
    $u = auth();
    locked("$dir/tokens.json", function ($t) use ($u) { unset($t[hash('sha256', $u['token'])]); return [$t, null]; });
    out(['ok' => true]);
}
case 'col': {
    $u = auth();
    $file = "$dir/col/" . $u['id'] . '.json';
    if ($method === 'GET') {
        $data = json_decode(@file_get_contents($file) ?: '[]', true) ?: [];
        out(['items' => array_values($data['items'] ?? [])]);
    }
    $in = body()['items'] ?? [];
    if (!is_array($in)) fail(400, 'items');
    $items = locked($file, function ($data) use ($in) {
        $items = $data['items'] ?? [];
        foreach ($in as $it) {
            if (!is_array($it) || !isset($it['key'], $it['t'])) continue;
            $k = mb_substr((string)$it['key'], 0, 300);
            if (!isset($items[$k]) || (int)$it['t'] >= (int)($items[$k]['t'] ?? 0)) $items[$k] = $it;
        }
        if (count($items) > 20000) fail(413, 'too_many');
        $data['items'] = $items;
        $data['updated'] = time();
        return [$data, $items];
    });
    out(['items' => array_values($items)]);
}
case 'img': {
    $q = mb_substr(trim($_GET['q'] ?? ''), 0, 120);
    if ($q === '') fail(400, 'q');
    if (!defined('EBAY_CLIENT_ID') || EBAY_CLIENT_ID === '') out(['url' => null, 'why' => 'no_ebay']);
    $cache = "$dir/img/" . hash('sha256', mb_strtolower($q)) . '.json';
    if (is_file($cache)) {
        $c = json_decode(file_get_contents($cache), true);
        $ttl = !empty($c['url']) ? 30 * 86400 : 86400;
        if (is_array($c) && time() - ($c['t'] ?? 0) < $ttl) out(['url' => $c['url'] ?? null]);
    }
    $url = ebayImage($q);
    file_put_contents($cache, json_encode(['url' => $url, 't' => time()]));
    out(['url' => $url]);
}
default:
    fail(404, 'action');
}

/** Token de aplicación de eBay (dura 2 horas; se guarda para no pedirlo cada vez). */
function ebayToken(): ?string {
    global $dir;
    $f = "$dir/ebay_token.json";
    $c = is_file($f) ? json_decode(file_get_contents($f), true) : null;
    if (is_array($c) && ($c['exp'] ?? 0) > time() + 60) return $c['token'];
    $ch = curl_init('https://api.ebay.com/identity/v1/oauth2/token');
    curl_setopt_array($ch, [
        CURLOPT_POST => true, CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 10,
        CURLOPT_HTTPHEADER => ['Content-Type: application/x-www-form-urlencoded', 'Authorization: Basic ' . base64_encode(EBAY_CLIENT_ID . ':' . EBAY_CLIENT_SECRET)],
        CURLOPT_POSTFIELDS => 'grant_type=client_credentials&scope=' . rawurlencode('https://api.ebay.com/oauth/api_scope'),
    ]);
    $r = json_decode(curl_exec($ch) ?: '', true);
    curl_close($ch);
    if (!is_array($r) || empty($r['access_token'])) return null;
    file_put_contents($f, json_encode(['token' => $r['access_token'], 'exp' => time() + (int)($r['expires_in'] ?? 7200)]));
    return $r['access_token'];
}

/** Primera foto de anuncios de eBay para ese coche (categoría Coches y vehículos de juguete). */
function ebayImage(string $q): ?string {
    $tok = ebayToken();
    if (!$tok) return null;
    $ch = curl_init('https://api.ebay.com/buy/browse/v1/item_summary/search?limit=5&category_ids=222&q=' . rawurlencode($q));
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 10,
        CURLOPT_HTTPHEADER => ['Authorization: Bearer ' . $tok, 'X-EBAY-C-MARKETPLACE-ID: EBAY_US'],
    ]);
    $r = json_decode(curl_exec($ch) ?: '', true);
    curl_close($ch);
    foreach ($r['itemSummaries'] ?? [] as $it) {
        $u = $it['image']['imageUrl'] ?? ($it['thumbnailImages'][0]['imageUrl'] ?? null);
        if ($u) return preg_replace('#/s-l\d+\.#', '/s-l500.', $u);
    }
    return null;
}
