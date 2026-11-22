<?php
// Copia este archivo como config.php en el servidor (NO lo subas a GitHub) y pon tus claves de eBay.
// Se consiguen gratis en developer.ebay.com → Application Keys → Production (App ID y Cert ID).
const EBAY_CLIENT_ID = '';
const EBAY_CLIENT_SECRET = '';

// Remitente de los correos (código de verificación y avisos)
const MAIL_FROM = 'Wolf Wheels <wolfwheels@jonatanmontero.es>';
// Te llega un correo con cada registro nuevo y cada cuenta verificada
const ADMIN_EMAIL = '';

// Envío por SMTP con el buzón de Wolf Wheels (recomendado: sin esto Gmail puede no entregar los correos)
const SMTP_USER = 'wolfwheels@jonatanmontero.es';
const SMTP_PASS = '';   // la contraseña del buzón
