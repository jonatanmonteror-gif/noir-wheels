<?php
/**
 * Wolf Wheels · cuentas, colección y fotos de respaldo.
 *
 * Instalación: sube esta carpeta (api.php + .htaccess) a /public_html/wolf-wheels/ de tu hosting.
 * config.php (NO va en GitHub), opcional:
 *   <?php const EBAY_CLIENT_ID = '…'; const EBAY_CLIENT_SECRET = '…'; const MAIL_FROM = 'Wolf Wheels <no-reply@tudominio>';
 *         const ADMIN_EMAIL = 'tu@correo';   // te avisa de cada registro nuevo y de cada cuenta verificada
 * La carpeta "data" se crea sola y queda protegida.
 *
 * Acciones (?a=…):
 *   register  POST {name,email,password}  → {pending:true}  (manda un código de 6 cifras al email)
 *   verify    POST {email,code}           → {token,name,email}  (activa la cuenta)
 *   resend    POST {email}                → {ok}  (vuelve a mandar el código)
 *   forgot    POST {email}                  → {ok}  (manda un código para cambiar la contraseña)
 *   reset     POST {email,code,password}    → {token,name,email}
 *   login     POST {email,password}       → {token,name,email}  ·  403 unverified si falta verificar
 *   me        GET  (Authorization: Bearer)
 *   logout    POST (Bearer)
 *   col       GET  (Bearer) → {items}   ·   POST {items} → mezcla por elemento (gana el cambio más reciente) → {items}
 *   public    GET/POST (Bearer) {on,slug,want} → ajustes del garaje público
 *   page      GET  u=slug → página web del garaje público (jonatanmontero.es/garaje/slug)
 *   img       GET  q=texto → {url}  (foto de un anuncio de eBay, con caché de 30 días)
 *   matches   GET  (Bearer) → {want:[…], trade:[…], public}  quién tiene lo que busco / quién busca lo que cambio
 *   contact   POST (Bearer) {slug,title,message} → {ok}  correo al dueño de un garaje público (responde a tu email)
 *   catadd    POST (Bearer) {id?,toy,title,brand,year,series,color,notes,image?} → {entry}  ficha nueva (o editar la tuya) en el catálogo de la comunidad
 *   catsearch GET  q=texto | toy=código | mine=1 (Bearer) → {items:[…]}
 *   catdel    POST (Bearer) {id} → {ok}  borra tu ficha (o cualquiera, si eres el administrador)
 *   catimg    GET  id=… → la foto de una ficha (jpeg)
 *   identify  POST (Bearer) {image: jpeg en base64} → {candidates:[{name,confidence,color,notes}]}  (IA de OpenRouter)
 */

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

const APP_KEY = 'wolfwheels-v1';
if (is_file(__DIR__ . '/config.php')) require __DIR__ . '/config.php';

function out($data, int $code = 200) { http_response_code($code); echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES); exit; }
function fail(int $code, string $msg) { out(['error' => $msg], $code); }

// La página pública del garaje se abre desde cualquier navegador; el resto solo desde la app
if (!in_array($_GET['a'] ?? '', ['page', 'catimg'], true) && ($_SERVER['HTTP_X_WOLF_APP'] ?? '') !== APP_KEY) fail(403, 'app');

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

/** Remitente de los correos: "Wolf Wheels <wolfwheels@dominio>" salvo que config.php diga otro. */
function sender(): string {
    $host = preg_replace('/^www\./', '', $_SERVER['HTTP_HOST'] ?? 'jonatanmontero.es');
    return defined('MAIL_FROM') ? MAIL_FROM : "Wolf Wheels <wolfwheels@$host>";
}

/**
 * Envía por SMTP con el buzón de Wolf Wheels (smtp.hostinger.com, SSL 465) si config.php tiene SMTP_PASS.
 * Así el correo sale firmado por el servidor de correo de Hostinger y Gmail lo acepta. Sin SMTP_PASS usa mail().
 */
function smtpSend(string $to, string $subjectEnc, string $html, string $fromHeader, string $replyTo = ''): bool {
    $user = defined('SMTP_USER') ? SMTP_USER : 'wolfwheels@jonatanmontero.es';
    $host = defined('SMTP_HOST') ? SMTP_HOST : 'smtp.hostinger.com';
    $fp = @stream_socket_client("ssl://$host:465", $errno, $errstr, 15);
    if (!$fp) { error_log("WOLF SMTP connect: $errstr"); return false; }
    stream_set_timeout($fp, 15);
    $read = function () use ($fp) { $out = ''; while (($l = fgets($fp, 515)) !== false) { $out .= $l; if (strlen($l) < 4 || $l[3] === ' ') break; } return $out; };
    $cmd = function (string $c, string $expect) use ($fp, $read) {
        if ($c !== '') fwrite($fp, $c . "\r\n");
        $r = $read();
        if (strpos($r, $expect) !== 0) { error_log('WOLF SMTP ' . (str_starts_with($c, 'AUTH') || strlen($c) > 40 ? substr($c, 0, 10) : $c) . ' -> ' . trim($r)); return false; }
        return true;
    };
    $ok = $cmd('', '220') && $cmd('EHLO jonatanmontero.es', '250') && $cmd('AUTH LOGIN', '334')
        && $cmd(base64_encode($user), '334') && $cmd(base64_encode(SMTP_PASS), '235')
        && $cmd("MAIL FROM:<$user>", '250') && $cmd("RCPT TO:<$to>", '250') && $cmd('DATA', '354');
    if ($ok) {
        $date = date('r');
        $id = bin2hex(random_bytes(12)) . '@jonatanmontero.es';
        $body = chunk_split(base64_encode($html));
        $rt = $replyTo !== '' ? $replyTo : $fromHeader;
        $msg = "Date: $date\r\nMessage-ID: <$id>\r\nFrom: $fromHeader\r\nReply-To: $rt\r\nTo: <$to>\r\nSubject: $subjectEnc\r\n"
            . "MIME-Version: 1.0\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n$body\r\n.";
        $ok = $cmd($msg, '250');
    }
    @fwrite($fp, "QUIT\r\n"); fclose($fp);
    return $ok;
}

/** Correo con la imagen de Wolf Wheels: logo, fondo oscuro y acento naranja. */
function wolfMail(string $to, string $subject, string $title, string $body, string $foot = '', string $replyTo = ''): bool {
    $host = $_SERVER['HTTP_HOST'] ?? 'jonatanmontero.es';
    $logo = "https://$host/wolf-wheels/logo.png";
    $html = '<!DOCTYPE html><html><body style="margin:0;padding:0;background:#0B0C0F">'
        . '<table width="100%" cellpadding="0" cellspacing="0" style="background:#0B0C0F;padding:24px 12px"><tr><td align="center">'
        . '<table width="100%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#15171C;border-radius:18px;overflow:hidden;border:1px solid #2A2E37">'
        . '<tr><td style="background:#0B0C0F;padding:18px 22px"><img src="' . $logo . '" alt="Wolf Wheels" width="260" style="display:block;width:260px;max-width:70%"></td></tr>'
        . '<tr><td style="height:4px;background:#FF5A1A;background-image:linear-gradient(90deg,#FF3D17,#FF5A1A,#FFB81C)"></td></tr>'
        . '<tr><td style="padding:26px 24px;font-family:Arial,Helvetica,sans-serif;color:#F4F4F5">'
        . '<div style="font-size:24px;font-weight:800;font-style:italic;text-transform:uppercase;letter-spacing:.5px;margin:0 0 14px">' . $title . '</div>'
        . '<div style="font-size:15px;line-height:22px;color:#C9CCD2">' . $body . '</div>'
        . '</td></tr>'
        . '<tr><td style="padding:16px 24px 22px;font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:18px;color:#6B707A;border-top:1px solid #2A2E37">'
        . ($foot !== '' ? $foot . '<br>' : '') . 'Wolf Wheels · tu garaje de Hot Wheels</td></tr>'
        . '</table></td></tr></table></body></html>';
    $subjectEnc = '=?UTF-8?B?' . base64_encode($subject) . '?=';
    $from = sender();
    if (defined('SMTP_PASS') && SMTP_PASS !== '') return smtpSend($to, $subjectEnc, $html, $from, $replyTo);
    $rt = $replyTo !== '' ? $replyTo : $from;
    $headers = "MIME-Version: 1.0\r\nContent-Type: text/html; charset=UTF-8\r\nFrom: $from\r\nReply-To: $rt\r\n";
    return @mail($to, $subjectEnc, $html, $headers, '-f' . preg_replace('/.*<([^>]+)>.*/', '$1', $from));
}

/** Crea un código nuevo para ese email, lo guarda (cifrado) y lo manda por correo. */
function sendCode(string $email, string $name, string $purpose = 'verify'): string {
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
    $safe = htmlspecialchars($name, ENT_QUOTES, 'UTF-8');
    $box = '<div style="margin:22px 0;padding:18px;background:#0B0C0F;border-radius:14px;text-align:center;border:1px solid #2A2E37">'
        . '<span style="font-size:40px;font-weight:800;font-style:italic;letter-spacing:10px;color:#FFB81C">' . $code . '</span></div>';
    if ($purpose === 'reset') {
        return wolfMail($email, "Cambia tu contraseña de Wolf Wheels: $code", 'Nueva contraseña',
            "Hola $safe, has pedido cambiar la contraseña de tu cuenta. Este es tu código:" . $box . 'Escríbelo en la app junto a tu contraseña nueva. Caduca en 30 minutos.',
            'Si no lo has pedido tú, ignora este correo: tu contraseña no cambia.') ? 'ok' : 'fail';
    }
    $body = "Hola $safe, ¡bienvenido al garaje! Este es tu código para activar la cuenta:"
        . '<div style="margin:22px 0;padding:18px;background:#0B0C0F;border-radius:14px;text-align:center;border:1px solid #2A2E37">'
        . '<span style="font-size:40px;font-weight:800;font-style:italic;letter-spacing:10px;color:#FFB81C">' . $code . '</span></div>'
        . 'Escríbelo en la app para empezar a guardar tu colección. El código caduca en 30 minutos.';
    return wolfMail($email, "Tu código de Wolf Wheels: $code", 'Activa tu cuenta', $body,
        'Si no has creado una cuenta en Wolf Wheels, ignora este correo.') ? 'ok' : 'fail';
}

/** Aviso al administrador (ADMIN_EMAIL en config.php) de registros y verificaciones. */
function notifyAdmin(string $what, string $name, string $email): void {
    if (!defined('ADMIN_EMAIL') || ADMIN_EMAIL === '') return;
    global $dir;
    $users = json_decode(@file_get_contents("$dir/users.json") ?: '[]', true) ?: [];
    $total = count(array_filter($users, fn($u) => !empty($u['verified'])));
    $h = fn($s) => htmlspecialchars($s, ENT_QUOTES, 'UTF-8');
    $when = date('d/m/Y H:i');
    $body = '<table cellpadding="0" cellspacing="0" style="font-size:15px;line-height:24px;color:#C9CCD2">'
        . '<tr><td style="color:#6B707A;padding-right:14px">Nombre</td><td style="color:#F4F4F5"><b>' . $h($name) . '</b></td></tr>'
        . '<tr><td style="color:#6B707A;padding-right:14px">Email</td><td style="color:#F4F4F5">' . $h($email) . '</td></tr>'
        . '<tr><td style="color:#6B707A;padding-right:14px">Fecha</td><td style="color:#F4F4F5">' . $when . '</td></tr></table>'
        . '<div style="margin-top:18px;padding:12px 14px;background:#0B0C0F;border-radius:12px;color:#FFB81C;font-weight:700">Cuentas verificadas en total: ' . $total . '</div>';
    wolfMail(ADMIN_EMAIL, "Wolf Wheels · $what: $name", $h($what), $body, 'Aviso automático para el administrador.');
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
    notifyAdmin('Nuevo registro (pendiente de verificar)', $name, $email);
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
    if (empty($u['notified'])) {
        notifyAdmin('Cuenta verificada', $u['name'], $email);
        locked("$dir/users.json", function ($users) use ($email) { if (isset($users[$email])) $users[$email]['notified'] = true; return [$users, null]; });
    }
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
case 'forgot': {
    if ($method !== 'POST') fail(405, 'method');
    $email = strtolower(trim(body()['email'] ?? ''));
    $users = json_decode(@file_get_contents("$dir/users.json") ?: '[]', true) ?: [];
    $u = $users[$email] ?? null;
    if (!$u) out(['ok' => true]);   // no se dice si el email existe
    $sc = sendCode($email, $u['name'], 'reset');
    if ($sc === 'rate') fail(429, 'mail_wait');
    if ($sc === 'fail') fail(502, 'mail');
    out(['ok' => true]);
}
case 'reset': {
    if ($method !== 'POST') fail(405, 'method');
    $b = body();
    $email = strtolower(trim($b['email'] ?? ''));
    $code = preg_replace('/\D/', '', (string)($b['code'] ?? ''));
    $pass = (string)($b['password'] ?? '');
    if (mb_strlen($pass) < 6) fail(400, 'password');
    if (tooMany($email)) fail(429, 'wait');
    $u = locked("$dir/users.json", function ($users) use ($email, $code, $pass) {
        $u = $users[$email] ?? null;
        if (!$u) return [null, 'wrong'];
        if (($u['codeExp'] ?? 0) < time() || ($u['codeTries'] ?? 0) >= 5) return [null, 'expired'];
        if (!password_verify($code, $u['code'] ?? '')) { $u['codeTries'] = ($u['codeTries'] ?? 0) + 1; $users[$email] = $u; return [$users, 'wrong']; }
        $u['hash'] = password_hash($pass, PASSWORD_DEFAULT);
        $u['verified'] = true;   // el código demuestra que el email es suyo
        unset($u['code'], $u['codeExp'], $u['codeTries']);
        $users[$email] = $u;
        return [$users, $u];
    });
    if ($u === 'wrong') { tooMany($email, true); fail(401, 'code'); }
    if ($u === 'expired') fail(410, 'expired');
    // Cierra las demás sesiones abiertas con la contraseña antigua
    locked("$dir/tokens.json", function ($t) use ($u) { foreach ($t as $k => $row) if (($row['u'] ?? '') === $u['id']) unset($t[$k]); return [$t, null]; });
    out(['token' => newToken($u['id']), 'name' => $u['name'], 'email' => $email]);
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
case 'sample': {
    // Correos de ejemplo al administrador. Solo funciona mientras config.php tenga SAMPLE_KEY.
    if (!defined('SAMPLE_KEY') || SAMPLE_KEY === '' || !hash_equals(SAMPLE_KEY, (string)($_GET['k'] ?? ''))) fail(404, 'action');
    if (!defined('ADMIN_EMAIL') || ADMIN_EMAIL === '') fail(400, 'admin');
    $code = '482913';
    $body = "Hola Jonatan, ¡bienvenido al garaje! Este es tu código para activar la cuenta:"
        . '<div style="margin:22px 0;padding:18px;background:#0B0C0F;border-radius:14px;text-align:center;border:1px solid #2A2E37">'
        . '<span style="font-size:40px;font-weight:800;font-style:italic;letter-spacing:10px;color:#FFB81C">' . $code . '</span></div>'
        . 'Escríbelo en la app para empezar a guardar tu colección. El código caduca en 30 minutos.';
    $a1 = wolfMail(ADMIN_EMAIL, "[EJEMPLO] Tu código de Wolf Wheels: $code", 'Activa tu cuenta', $body,
        'Si no has creado una cuenta en Wolf Wheels, ignora este correo.');
    $users = json_decode(@file_get_contents("$dir/users.json") ?: '[]', true) ?: [];
    $total = count(array_filter($users, fn($u) => !empty($u['verified'])));
    $b2 = '<table cellpadding="0" cellspacing="0" style="font-size:15px;line-height:24px;color:#C9CCD2">'
        . '<tr><td style="color:#6B707A;padding-right:14px">Nombre</td><td style="color:#F4F4F5"><b>Usuario de ejemplo</b></td></tr>'
        . '<tr><td style="color:#6B707A;padding-right:14px">Email</td><td style="color:#F4F4F5">ejemplo@correo.com</td></tr>'
        . '<tr><td style="color:#6B707A;padding-right:14px">Fecha</td><td style="color:#F4F4F5">' . date('d/m/Y H:i') . '</td></tr></table>'
        . '<div style="margin-top:18px;padding:12px 14px;background:#0B0C0F;border-radius:12px;color:#FFB81C;font-weight:700">Cuentas verificadas en total: ' . $total . '</div>';
    $a2 = wolfMail(ADMIN_EMAIL, '[EJEMPLO] Wolf Wheels · Nuevo registro: Usuario de ejemplo', 'Nuevo registro', $b2, 'Aviso automático para el administrador.');
    out(['code_mail' => $a1, 'admin_mail' => $a2]);
}
case 'public': {
    $u = auth();
    if ($method === 'GET') out(['on' => !empty($u['public']['on']), 'slug' => $u['public']['slug'] ?? '', 'want' => !empty($u['public']['want'])]);
    $b = body();
    $on = !empty($b['on']); $want = !empty($b['want']);
    $slug = strtolower(trim((string)($b['slug'] ?? '')));
    $slug = preg_replace('/[^a-z0-9-]/', '', strtr($slug, ['á'=>'a','é'=>'e','í'=>'i','ó'=>'o','ú'=>'u','ñ'=>'n',' '=>'-']));
    if ($on && !preg_match('/^[a-z0-9][a-z0-9-]{2,29}$/', $slug)) fail(400, 'slug');
    $r = locked("$dir/public.json", function ($map) use ($slug, $u, $on) {
        foreach ($map as $s => $uid) if ($uid === $u['id'] && $s !== $slug) unset($map[$s]);
        if ($on) {
            if (isset($map[$slug]) && $map[$slug] !== $u['id']) return [null, 'taken'];
            $map[$slug] = $u['id'];
        } else unset($map[$slug]);
        return [$map, 'ok'];
    });
    if ($r === 'taken') fail(409, 'slug_taken');
    locked("$dir/users.json", function ($users) use ($u, $on, $slug, $want) {
        if (isset($users[$u['email']])) $users[$u['email']]['public'] = ['on' => $on, 'slug' => $slug, 'want' => $want];
        return [$users, null];
    });
    $host = $_SERVER['HTTP_HOST'] ?? 'jonatanmontero.es';
    out(['on' => $on, 'slug' => $slug, 'want' => $want, 'url' => $on ? "https://$host/garaje/$slug" : null]);
}
case 'page': {
    header('Content-Type: text/html; charset=utf-8');
    header('Cache-Control: public, max-age=120');
    $slug = strtolower((string)($_GET['u'] ?? ''));
    $map = json_decode(@file_get_contents("$dir/public.json") ?: '[]', true) ?: [];
    $uid = $map[$slug] ?? null;
    $users = json_decode(@file_get_contents("$dir/users.json") ?: '[]', true) ?: [];
    $user = null;
    foreach ($users as $e => $x) if (($x['id'] ?? '') === $uid && !empty($x['public']['on'])) $user = $x;
    if (!$user) { http_response_code(404); echo garagePage(null, [], false); exit; }
    $col = json_decode(@file_get_contents("$dir/col/$uid.json") ?: '[]', true) ?: [];
    echo garagePage($user, array_values($col['items'] ?? []), !empty($user['public']['want']));
    exit;
}
case 'matches': {
    $u = auth();
    $mine = json_decode(@file_get_contents("$dir/col/" . $u['id'] . '.json') ?: '[]', true) ?: [];
    $mine = array_filter($mine['items'] ?? [], fn($i) => empty($i['deleted']));
    $myWant = array_values(array_filter($mine, fn($i) => ($i['status'] ?? '') === 'want'));
    $myTrade = array_values(array_filter($mine, fn($i) => ($i['status'] ?? '') === 'have' && (!empty($i['trade']) || (int)($i['qty'] ?? 1) > 1)));
    $norm = fn($s) => mb_strtolower(trim((string)$s));
    $map = json_decode(@file_get_contents("$dir/public.json") ?: '[]', true) ?: [];
    $users = json_decode(@file_get_contents("$dir/users.json") ?: '[]', true) ?: [];
    $byId = [];
    foreach ($users as $x) if (!empty($x['id'])) $byId[$x['id']] = $x;
    $wantOut = []; $tradeOut = [];
    foreach ($myWant as $w) $wantOut[$w['key']] = ['item' => $w, 'offers' => []];
    foreach ($myTrade as $t) $tradeOut[$t['key']] = ['item' => $t, 'seekers' => []];
    foreach ($map as $slug => $uid) {
        if ($uid === $u['id'] || empty($byId[$uid]['public']['on'])) continue;
        $owner = $byId[$uid];
        $col = json_decode(@file_get_contents("$dir/col/$uid.json") ?: '[]', true) ?: [];
        $items = array_filter($col['items'] ?? [], fn($i) => empty($i['deleted']));
        $who = ['slug' => $slug, 'name' => $owner['name'] ?? 'Coleccionista'];
        $haveByKey = []; $haveByTitle = []; $wantByKey = []; $wantByTitle = [];
        foreach ($items as $i) {
            $st = $i['status'] ?? '';
            if ($st === 'have') { $haveByKey[$i['key']] = $i; $haveByTitle[$norm($i['title'] ?? '')][] = $i; }
            if ($st === 'want' && !empty($owner['public']['want'])) { $wantByKey[$i['key']] = $i; $wantByTitle[$norm($i['title'] ?? '')][] = $i; }
        }
        foreach ($wantOut as $k => &$row) {
            if (isset($haveByKey[$k])) {
                $h = $haveByKey[$k];
                $row['offers'][] = $who + ['kind' => (!empty($h['trade']) || (int)($h['qty'] ?? 1) > 1) ? 'trade' : 'have',
                    'year' => $h['year'] ?? '', 'color' => $h['color'] ?? '', 'photo' => $h['photo'] ?? '', 'qty' => (int)($h['qty'] ?? 1)];
            } elseif (!empty($haveByTitle[$norm($row['item']['title'] ?? '')])) {
                $alts = $haveByTitle[$norm($row['item']['title'] ?? '')];
                usort($alts, fn($a, $b) => (int)!empty($b['trade']) <=> (int)!empty($a['trade']));
                $h = $alts[0];
                $row['offers'][] = $who + ['kind' => 'model', 'year' => $h['year'] ?? '', 'color' => $h['color'] ?? '',
                    'photo' => $h['photo'] ?? '', 'trade' => !empty($h['trade']), 'versions' => count($alts)];
            }
        }
        unset($row);
        foreach ($tradeOut as $k => &$row) {
            if (isset($wantByKey[$k])) $row['seekers'][] = $who + ['kind' => 'exact'];
            elseif (!empty($wantByTitle[$norm($row['item']['title'] ?? '')])) $row['seekers'][] = $who + ['kind' => 'model'];
        }
        unset($row);
    }
    $rank = ['trade' => 0, 'have' => 1, 'model' => 2, 'exact' => 0];
    $clean = function ($rows, $field) use ($rank) {
        $rows = array_values(array_filter($rows, fn($r) => count($r[$field]) > 0));
        foreach ($rows as &$r) {
            usort($r[$field], fn($a, $b) => $rank[$a['kind']] <=> $rank[$b['kind']]);
            $r[$field] = array_slice($r[$field], 0, 30);
            $i = $r['item'];
            $r['item'] = ['key' => $i['key'], 'title' => $i['title'] ?? '', 'year' => $i['year'] ?? '', 'color' => $i['color'] ?? '',
                'series' => $i['series'] ?? '', 'photo' => $i['photo'] ?? '', 'toy' => $i['toy'] ?? ''];
        }
        usort($rows, fn($a, $b) => $rank[$a[$field][0]['kind']] <=> $rank[$b[$field][0]['kind']] ?: count($b[$field]) <=> count($a[$field]));
        return $rows;
    };
    out(['want' => $clean(array_values($wantOut), 'offers'), 'trade' => $clean(array_values($tradeOut), 'seekers'),
        'public' => !empty($u['public']['on']), 'wants' => count($myWant), 'trades' => count($myTrade), 'garages' => max(0, count($map) - (isset(array_flip($map)[$u['id']]) ? 1 : 0))]);
}
case 'contact': {
    $u = auth();
    $b = body();
    $slug = strtolower((string)($b['slug'] ?? ''));
    $title = mb_substr(trim(strip_tags((string)($b['title'] ?? ''))), 0, 120);
    $msg = mb_substr(trim(strip_tags((string)($b['message'] ?? ''))), 0, 1500);
    if ($msg === '') fail(400, 'message');
    $map = json_decode(@file_get_contents("$dir/public.json") ?: '[]', true) ?: [];
    $uid = $map[$slug] ?? null;
    if (!$uid || $uid === $u['id']) fail(404, 'garage');
    $users = json_decode(@file_get_contents("$dir/users.json") ?: '[]', true) ?: [];
    $toEmail = null; $toName = '';
    foreach ($users as $e => $x) if (($x['id'] ?? '') === $uid && !empty($x['public']['on'])) { $toEmail = $e; $toName = $x['name'] ?? ''; }
    if (!$toEmail) fail(404, 'garage');
    if (quota('contact:' . $u['id'], 15, 86400)) fail(429, 'too_many');
    $h = fn($s) => htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8');
    $from = $u['name'] ?? 'Un coleccionista';
    $mySlug = $u['public']['slug'] ?? '';
    $host = $_SERVER['HTTP_HOST'] ?? 'jonatanmontero.es';
    $body = 'Hola ' . $h($toName) . ', <b style="color:#F4F4F5">' . $h($from) . '</b> ha visto tu garaje en Wolf Wheels'
        . ($title !== '' ? ' y le interesa <b style="color:#FFB81C">' . $h($title) . '</b>' : '') . '.'
        . '<div style="margin:18px 0;padding:14px 16px;background:#0B0C0F;border-radius:12px;border-left:4px solid #FF5A1A;color:#F4F4F5;white-space:pre-wrap">' . $h($msg) . '</div>'
        . 'Para contestarle, responde a este correo: le llegará directamente a ' . $h($from) . '.'
        . (!empty($u['public']['on']) && $mySlug !== '' ? '<br><br>Su garaje: <a style="color:#FF7A1A" href="https://' . $h($host) . '/garaje/' . $h($mySlug) . '">' . $h($host) . '/garaje/' . $h($mySlug) . '</a>' : '');
    $replyTo = '=?UTF-8?B?' . base64_encode($from) . '?= <' . $u['email'] . '>';
    $ok = wolfMail($toEmail, "Wolf Wheels · $from quiere hablar contigo" . ($title !== '' ? " sobre $title" : ''), 'Mensaje de un coleccionista', $body,
        'Te llega porque tu garaje de Wolf Wheels es público. Puedes ocultarlo desde la app, en Mi garaje.', $replyTo);
    if (!$ok) fail(502, 'mail');
    out(['ok' => true]);
}
case 'catadd': {
    $u = auth();
    $b = body();
    $clean = fn($k, $n) => mb_substr(trim(strip_tags((string)($b[$k] ?? ''))), 0, $n);
    $title = $clean('title', 60);
    if ($title === '') fail(400, 'title');
    $toy = strtoupper(preg_replace('/[^A-Za-z0-9]/', '', $clean('toy', 12)));
    $img = (string)($b['image'] ?? '');
    $raw = null;
    if ($img !== '') {
        $raw = base64_decode($img, true);
        if ($raw === false || strlen($raw) > 2500000 || substr($raw, 0, 3) !== "\xFF\xD8\xFF") fail(400, 'image');
    }
    $id = preg_replace('/[^a-z0-9]/', '', (string)($b['id'] ?? ''));
    if ($id === '' && quota('catadd:' . $u['id'], 40, 86400)) fail(429, 'too_many');
    $isNew = $id === '';
    if ($isNew) $id = bin2hex(random_bytes(6));
    $admin = defined('ADMIN_EMAIL') && strcasecmp(ADMIN_EMAIL, $u['email']) === 0;
    $entry = locked("$dir/catalog.json", function ($cat) use ($id, $isNew, $u, $admin, $clean, $title, $toy, $raw) {
        $old = $cat[$id] ?? null;
        if (!$isNew && (!$old || ($old['uid'] !== $u['id'] && !$admin))) return [null, null];
        $e = ['id' => $id, 'title' => $title, 'brand' => $clean('brand', 40) ?: 'Hot Wheels', 'year' => preg_replace('/\D/', '', $clean('year', 4)),
            'series' => $clean('series', 60), 'color' => $clean('color', 40), 'toy' => $toy, 'notes' => $clean('notes', 300),
            'uid' => $old['uid'] ?? $u['id'], 'by' => $old['by'] ?? ($u['name'] ?? ''), 'created' => $old['created'] ?? time(), 't' => time(),
            'photo' => $raw !== null ? time() : ($old['photo'] ?? 0)];
        $cat[$id] = $e;
        return [$cat, $e];
    });
    if (!$entry) fail(404, 'entry');
    if ($raw !== null) { @mkdir("$dir/cat", 0755, true); file_put_contents("$dir/cat/$id.jpg", $raw); }
    if ($isNew && defined('ADMIN_EMAIL') && ADMIN_EMAIL !== '') {
        $h = fn($s) => htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8');
        $host = $_SERVER['HTTP_HOST'] ?? 'jonatanmontero.es';
        $body = '<b style="color:#F4F4F5">' . $h($entry['by']) . '</b> ha añadido una ficha al catálogo de la comunidad:'
            . '<div style="margin:14px 0;padding:14px;background:#0B0C0F;border-radius:12px"><b style="color:#FFB81C;font-size:18px">' . $h($title) . '</b><br>'
            . $h(implode(' · ', array_filter([$entry['brand'], $entry['year'], $entry['color'], $entry['series'], $toy ? "Nº $toy" : '']))) . '</div>'
            . ($entry['photo'] ? '<img src="https://' . $h($host) . '/wolf-wheels/api.php?a=catimg&id=' . $id . '" width="460" style="max-width:100%;border-radius:12px">' : '');
        wolfMail(ADMIN_EMAIL, "Wolf Wheels · Ficha nueva: $title", 'Ficha nueva', $body, 'Si no es correcta, bórrala desde la app (abriendo la ficha, con tu cuenta de administrador).');
    }
    out(['entry' => catOut($entry)]);
}
case 'catsearch': {
    $cat = json_decode(@file_get_contents("$dir/catalog.json") ?: '[]', true) ?: [];
    $q = mb_strtolower(trim((string)($_GET['q'] ?? '')));
    $toy = strtoupper(preg_replace('/[^A-Za-z0-9]/', '', (string)($_GET['toy'] ?? '')));
    $id = (string)($_GET['id'] ?? '');
    $hasTok = ($_SERVER['HTTP_AUTHORIZATION'] ?? ($_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? ($_SERVER['HTTP_X_WOLF_TOKEN'] ?? ''))) !== '';
    if ($hasTok || !empty($_GET['mine'])) $u = auth();
    $mine = !empty($_GET['mine']) ? $u['id'] : null;
    $res = [];
    foreach ($cat as $e) {
        if ($id !== '') { if ($e['id'] === $id) $res[] = $e; continue; }
        if ($mine !== null) { if ($e['uid'] === $mine) $res[] = $e; continue; }
        if ($toy !== '') { if ($e['toy'] !== '' && str_contains($e['toy'], $toy)) $res[] = $e; continue; }
        if ($q !== '' && mb_strlen($q) >= 2) {
            $hay = mb_strtolower($e['title'] . ' ' . $e['brand'] . ' ' . $e['series'] . ' ' . $e['toy'] . ' ' . $e['color']);
            $ok = true;
            foreach (preg_split('/\s+/', $q) as $w) if ($w !== '' && !str_contains($hay, $w)) { $ok = false; break; }
            if ($ok) $res[] = $e;
        }
    }
    usort($res, fn($a, $b) => $b['t'] <=> $a['t']);
    out(['items' => array_map('catOut', array_slice($res, 0, 40))]);
}
case 'catdel': {
    $u = auth();
    $id = preg_replace('/[^a-z0-9]/', '', (string)(body()['id'] ?? ''));
    $admin = defined('ADMIN_EMAIL') && strcasecmp(ADMIN_EMAIL, $u['email']) === 0;
    $ok = locked("$dir/catalog.json", function ($cat) use ($id, $u, $admin) {
        if (!isset($cat[$id]) || ($cat[$id]['uid'] !== $u['id'] && !$admin)) return [null, false];
        unset($cat[$id]);
        return [$cat, true];
    });
    if (!$ok) fail(404, 'entry');
    @unlink("$dir/cat/$id.jpg");
    out(['ok' => true]);
}
case 'catimg': {
    $id = preg_replace('/[^a-z0-9]/', '', (string)($_GET['id'] ?? ''));
    $f = "$dir/cat/$id.jpg";
    if ($id === '' || !is_file($f)) { http_response_code(404); exit; }
    header('Content-Type: image/jpeg');
    header('Cache-Control: public, max-age=2592000');
    readfile($f);
    exit;
}
case 'identify': {
    $u = auth();
    if (!defined('OPENROUTER_KEY') || OPENROUTER_KEY === '') fail(503, 'no_ai');
    $img = (string)(body()['image'] ?? '');
    if (strlen($img) < 1000 || strlen($img) > 3000000) fail(400, 'image');
    $raw = base64_decode($img, true);
    if ($raw === false || substr($raw, 0, 3) !== "\xFF\xD8\xFF") fail(400, 'image');
    if (quota('identify:' . $u['id'], 40, 86400)) fail(429, 'too_many');
    $model = defined('OPENROUTER_MODEL') && OPENROUTER_MODEL !== '' ? OPENROUTER_MODEL : 'google/gemini-2.5-flash';
    $prompt = 'You are an expert Hot Wheels die-cast collector. Identify the Hot Wheels car in the photo (it is loose, without card). '
        . 'Return ONLY JSON: {"candidates":[{"name":"exact casting name as written in the Hot Wheels Wiki (hotwheels.fandom.com) page title","confidence":0-100,"color":"main body color in Spanish","notes":"short Spanish hint: tampos, wheels, year range"}]}. '
        . 'Give 1 to 3 candidates, best first. If it is not a Hot Wheels car, or you cannot tell, return {"candidates":[]}.';
    $payload = ['model' => $model, 'temperature' => 0.2, 'max_tokens' => 600,
        'response_format' => ['type' => 'json_object'],
        'messages' => [['role' => 'user', 'content' => [
            ['type' => 'text', 'text' => $prompt],
            ['type' => 'image_url', 'image_url' => ['url' => 'data:image/jpeg;base64,' . $img]],
        ]]]];
    $ch = curl_init('https://openrouter.ai/api/v1/chat/completions');
    curl_setopt_array($ch, [CURLOPT_POST => true, CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 45,
        CURLOPT_HTTPHEADER => ['Authorization: Bearer ' . OPENROUTER_KEY, 'Content-Type: application/json',
            'HTTP-Referer: https://jonatanmontero.es', 'X-Title: Wolf Wheels'],
        CURLOPT_POSTFIELDS => json_encode($payload)]);
    $res = curl_exec($ch); $code = curl_getinfo($ch, CURLINFO_HTTP_CODE); curl_close($ch);
    if ($res === false || $code >= 400) { error_log("WOLF identify $code " . substr((string)$res, 0, 300)); fail(502, 'ai'); }
    $txt = json_decode($res, true)['choices'][0]['message']['content'] ?? '';
    if (preg_match('/\{.*\}/s', $txt, $m)) $txt = $m[0];
    $j = json_decode($txt, true);
    $out = [];
    foreach (($j['candidates'] ?? []) as $c) {
        $n = trim((string)($c['name'] ?? ''));
        if ($n === '') continue;
        $out[] = ['name' => mb_substr($n, 0, 80), 'confidence' => max(0, min(100, (int)($c['confidence'] ?? 0))),
            'color' => mb_substr((string)($c['color'] ?? ''), 0, 40), 'notes' => mb_substr((string)($c['notes'] ?? ''), 0, 200)];
        if (count($out) >= 3) break;
    }
    out(['candidates' => $out]);
}
default:
    fail(404, 'action');
}

/** Ficha del catálogo tal como la ve la app (sin el id interno del autor). */
function catOut(array $e): array {
    global $u;
    $host = $_SERVER['HTTP_HOST'] ?? 'jonatanmontero.es';
    $o = $e; unset($o['uid']);
    $o['photo'] = !empty($e['photo']) ? "https://$host/wolf-wheels/api.php?a=catimg&id={$e['id']}&v={$e['photo']}" : '';
    $o['mine'] = isset($u['id']) && $u['id'] === $e['uid'];
    return $o;
}

/** Límite de uso por usuario y acción: true si ya ha llegado al máximo en la ventana de tiempo. */
function quota(string $key, int $max, int $window): bool {
    global $dir;
    return locked("$dir/quota.json", function ($a) use ($key, $max, $window) {
        $now = time();
        foreach ($a as $k => $l) { $a[$k] = array_values(array_filter($l, fn($t) => $t > $now - 86400 * 2)); if (!$a[$k]) unset($a[$k]); }
        $list = array_values(array_filter($a[$key] ?? [], fn($t) => $t > $now - $window));
        if (count($list) >= $max) return [$a, true];
        $list[] = $now; $a[$key] = $list;
        return [$a, false];
    });
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

/** Página web pública del garaje (solo lectura). */
function garagePage(?array $user, array $items, bool $showWant): string {
    $h = fn($s) => htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8');
    $host = $_SERVER['HTTP_HOST'] ?? 'jonatanmontero.es';
    $logo = "https://$host/wolf-wheels/logo.png";
    $css = '*{box-sizing:border-box;margin:0;padding:0}body{background:#0B0C0F;color:#F4F4F5;font:15px/1.5 Inter,system-ui,sans-serif}'
        . '.w{max-width:1100px;margin:0 auto;padding:22px 18px 60px}header img{width:230px;max-width:60%}'
        . 'h1{font:800 italic 40px/1.05 "Barlow Condensed",Inter,sans-serif;text-transform:uppercase;margin:26px 0 6px}'
        . 'h1 span{background:linear-gradient(90deg,#FF3D17,#FFB81C);-webkit-background-clip:text;background-clip:text;color:transparent}'
        . '.stats{display:flex;gap:10px;flex-wrap:wrap;margin:14px 0 6px}.st{background:#15171C;border:1px solid #2A2E37;border-radius:12px;padding:8px 14px}'
        . '.st b{font:800 italic 26px "Barlow Condensed",sans-serif;color:#FFB81C;display:block;line-height:1}.st small{color:#6B707A;text-transform:uppercase;letter-spacing:1px;font-size:11px}'
        . 'h2{font:800 italic 22px "Barlow Condensed",sans-serif;text-transform:uppercase;margin:30px 0 12px;display:flex;align-items:center;gap:10px}'
        . 'h2:before{content:"";width:14px;height:16px;background:linear-gradient(90deg,#FF3D17,#FFB81C);transform:skewX(-16deg)}'
        . '.g{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:10px}'
        . '.c{background:#15171C;border:1px solid #2A2E37;border-radius:14px;overflow:hidden}.c .im{aspect-ratio:4/3;background:#ECECEF;display:flex;align-items:center;justify-content:center;position:relative}'
        . '.c .im img{max-width:94%;max-height:94%;object-fit:contain}.c .t{padding:8px 10px 10px}.c .n{font:700 italic 16px/1.15 "Barlow Condensed",sans-serif;text-transform:uppercase}'
        . '.c .s{color:#A3A7B0;font-size:12px}.bd{position:absolute;top:6px;left:6px;background:#FFB81C;color:#14100A;font:800 italic 11px "Barlow Condensed",sans-serif;padding:1px 7px;transform:skewX(-12deg)}'
        . '.q{position:absolute;top:6px;right:6px;background:#0B0C0F;color:#FFB81C;font-weight:700;font-size:12px;border-radius:99px;padding:1px 8px}'
        . 'footer{margin-top:50px;color:#6B707A;font-size:13px}footer a{color:#FF7A1A}';
    $head = '<!DOCTYPE html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
        . '<link href="https://fonts.googleapis.com/css2?family=Barlow+Condensed:ital,wght@1,700;1,800&family=Inter:wght@400;600&display=swap" rel="stylesheet">'
        . '<style>' . $css . '</style>';
    if (!$user) return $head . '<title>Wolf Wheels</title></head><body><div class="w"><header><img src="' . $logo . '" alt="Wolf Wheels"></header><h1>Este garaje <span>no es público</span></h1><p style="color:#A3A7B0">O el enlace no es correcto.</p></div></body></html>';
    $items = array_filter($items, fn($i) => empty($i['deleted']));
    $have = array_values(array_filter($items, fn($i) => ($i['status'] ?? '') === 'have'));
    usort($have, fn($a, $b) => ($b['added'] ?? 0) <=> ($a['added'] ?? 0));
    $want = array_values(array_filter($items, fn($i) => ($i['status'] ?? '') === 'want'));
    $trade = array_values(array_filter($have, fn($i) => !empty($i['trade'])));
    $cars = array_sum(array_map(fn($i) => max(1, (int)($i['qty'] ?? 1)), $have));
    $models = count(array_unique(array_map(fn($i) => $i['title'] ?? '', $have)));
    $card = function ($i, $extra = '') use ($h) {
        $img = !empty($i['photo']) ? '<img loading="lazy" src="' . $h($i['photo']) . '" alt="">' : '';
        $th = (int)($i['th'] ?? 0) === 2 ? '<span class="bd">STH</span>' : ((int)($i['th'] ?? 0) === 1 ? '<span class="bd">TH</span>' : '');
        $q = $extra !== '' ? '<span class="q">' . $h($extra) . '</span>' : '';
        return '<div class="c"><div class="im">' . $img . $th . $q . '</div><div class="t"><div class="n">' . $h($i['title'] ?? '') . '</div>'
            . '<div class="s">' . $h(trim(($i['year'] ?? '') . ' · ' . ($i['color'] ?? ''), ' ·')) . '</div></div></div>';
    };
    $name = $user['name'] ?? 'Coleccionista';
    $out = $head . '<title>Garaje de ' . $h($name) . ' · Wolf Wheels</title><meta property="og:title" content="El garaje de ' . $h($name) . '">'
        . '<meta property="og:image" content="' . $logo . '"></head><body><div class="w"><header><img src="' . $logo . '" alt="Wolf Wheels"></header>'
        . '<h1>El garaje de <span>' . $h($name) . '</span></h1><div class="stats"><div class="st"><b>' . $cars . '</b><small>coches</small></div>'
        . '<div class="st"><b>' . $models . '</b><small>modelos</small></div><div class="st"><b>' . count($trade) . '</b><small>para cambiar</small></div></div>';
    if ($trade) {
        $out .= '<h2>Para cambiar</h2><div class="g">';
        foreach ($trade as $i) { $q = max(1, (int)($i['qty'] ?? 1)); $out .= $card($i, $q > 1 ? '×' . ($q - 1) . ' repe' . ($q > 2 ? 's' : '') : 'cambio'); }
        $out .= '</div>';
    }
    if ($showWant && $want) {
        $out .= '<h2>Busco</h2><div class="g">';
        foreach ($want as $i) $out .= $card($i);
        $out .= '</div>';
    }
    $out .= '<h2>Mi garaje</h2><div class="g">';
    foreach ($have as $i) { $q = max(1, (int)($i['qty'] ?? 1)); $out .= $card($i, $q > 1 ? '×' . $q : ''); }
    if (!$have) $out .= '<p style="color:#A3A7B0">Aún vacío.</p>';
    return $out . '</div><footer>Hecho con <b>Wolf Wheels</b> · tu garaje de Hot Wheels</footer></div></body></html>';
}
